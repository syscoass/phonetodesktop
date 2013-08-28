/*******************************************************************************
 * Copyright (c) 2013 Humberto Fraga <xisberto@gmail.com>.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 * 
 * Contributors:
 *     Humberto Fraga <xisberto@gmail.com> - initial API and implementation
 ******************************************************************************/
package net.xisberto.phonetodesktop.database;

import java.util.ArrayList;
import java.util.List;

import net.xisberto.phonetodesktop.model.LocalTask;
import net.xisberto.phonetodesktop.model.LocalTask.Status;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {
	public static final String DATABASE_NAME = "phonetodesktop";
	public static final int DATABASE_VERSION = 1;

	private static DatabaseHelper instance;

	private DatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	public static synchronized DatabaseHelper getInstance(Context context) {
		if (instance == null) {
			instance = new DatabaseHelper(context.getApplicationContext());
		}
		return instance;
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(TableTasks.CREATE_SQL);
	}

	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		// TODO Auto-generated method stub

	}

	private ContentValues contentValuesFromTask(LocalTask task) {
		ContentValues cv = new ContentValues();
		cv.put(TableTasks.COLUMN_GOOGLE_ID, task.getGoogleId());
		cv.put(TableTasks.COLUMN_TITLE, task.getTitle());
		cv.put(TableTasks.COLUMN_DESCRIPTION, task.getDescription());
		cv.put(TableTasks.COLUMN_STATUS, task.getStatus().name());
		return cv;
	}

	private LocalTask taskFromCursor(Cursor c) {
		LocalTask result = new LocalTask(this);
		result.setLocalId(c.getLong(0)).setGoogleId(c.getString(1))
				.setTitle(c.getString(2)).setDescription(c.getString(3))
				.setStatus(Status.valueOf(c.getString(4)));
		return result;
	}

	public long insert(LocalTask task) throws SQLException {
		final ContentValues cv = contentValuesFromTask(task);
		final SQLiteDatabase db = getWritableDatabase();
		return db.insertOrThrow(TableTasks.TABLE_NAME, null, cv);
	}

	public void update(LocalTask task) {
		final SQLiteDatabase db = getWritableDatabase();
		try {
			final ContentValues cv = contentValuesFromTask(task);
			db.update(TableTasks.TABLE_NAME, cv, TableTasks.COLUMN_LOCAL_ID + " = ?",
					new String[] { Long.toString(task.getLocalId()) });
		} finally {

		}
	}

	public int getTasksCount() {
		final SQLiteDatabase db = getReadableDatabase();
		final Cursor cursor = db.query(TableTasks.TABLE_NAME,
				TableTasks.COLUMNS, null, null, null, null, TableTasks.COLUMN_LOCAL_ID);
		return cursor.getCount();
	}

	public LocalTask getTask(long local_id) {
		final SQLiteDatabase db = getReadableDatabase();
		final Cursor cursor = db.query(TableTasks.TABLE_NAME,
				TableTasks.COLUMNS, TableTasks.COLUMN_LOCAL_ID + " = ?",
				new String[] { Long.toString(local_id) }, null, null, null);
		try {
			if (cursor.getCount() == 1) {
				cursor.moveToFirst();
				final LocalTask task = taskFromCursor(cursor);
				return task;
			} else {
				return null;
			}
		} finally {
			cursor.close();
		}
	}
	
	public Cursor listTasksAsCursor() {
		SQLiteDatabase db = getReadableDatabase();
		return db.query(TableTasks.TABLE_NAME,
				TableTasks.COLUMNS, null, null, null, null, TableTasks.COLUMN_LOCAL_ID);
	}

	public List<LocalTask> listTasks() {
		List<LocalTask> tasks = new ArrayList<LocalTask>();

		Cursor cursor = listTasksAsCursor();
		try {
			while (cursor.moveToNext()) {
				LocalTask task = taskFromCursor(cursor);
				tasks.add(task);
			}
		} finally {
			cursor.close();
		}
		return tasks;
	}
}
