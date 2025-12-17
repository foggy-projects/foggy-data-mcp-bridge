/*******************************************************************************
 * This software is subject to the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * Agreement, available at the following URL:
 * http://www.gnu.org/licenses/lgpl.html
 * Copyright (c) 2012, 2013  Foggy.
 * All rights reserved. 
 * You must accept the terms of that agreement to use this software.
 *******************************************************************************/
package com.foggyframework.core.utils.file;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

/**
 * 
 * 跟踪文件变化事件,但不会跟踪到文件重命名.
 * 
 * @author Foggy
 * 
 */
@Slf4j
public class FileTracer {

	public final static class FileListener {
		File file;
		FileTracer tracer;
		Long modifyTime;

		public FileListener(File file, FileTracer tracer) {
			super();
			this.file = file;
			this.tracer = tracer;
			modifyTime = file.lastModified();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			FileListener other = (FileListener) obj;
			if (file == null) {
				if (other.file != null)
					return false;
			} else if (!file.equals(other.file))
				return false;
			if (tracer == null) {
                return other.tracer == null;
			} else return tracer.equals(other.tracer);
        }

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((file == null) ? 0 : file.hashCode());
			result = prime * result + ((tracer == null) ? 0 : tracer.hashCode());
			return result;
		}
	}

	private final static class Scaner extends TimerTask {

		private final List<FileListener> files = new ArrayList<FileListener>();
		private final List<FileListener> tmpFiles = new ArrayList<FileListener>();
		private final Object lock = new Object();

		public void addFile(FileTracer tracer, File file) {
			FileListener fl = new FileListener(file, tracer);
			if (scaning) {
				tmpFiles.add(fl);
				return;
			}
			// synchronized (lock) {

			if (!files.contains(fl)) {
				files.add(fl);
			}
			// }
		}

		@Override
		public void run() {
			scan();
		}

		private static boolean scaning = false;

		private void scan() {
			synchronized (lock) {
				scaning = true;
				List<FileListener> removed = null;
				try {
					// for (FileListener o : files.toArray(new FileListener[0]))
					// {
					for (FileListener o : files.toArray(new FileListener[0])) {
						if(o==null || o.file==null){
							if (removed == null) {
								removed = new ArrayList<FileListener>();
							}
							removed.add(o);
						}else {
							File f = o.file;
							Long l = o.modifyTime;
							if (!f.exists()) {
								// 文件被删除了
								if (removed == null) {
									removed = new ArrayList<FileListener>();
								}
								// fileToLastModifyTime.remove(f);
								removed.add(o);
								try {
									o.tracer.fileDeleted(f);
								} catch (Throwable t) {
									log.error(t.getMessage());
									t.printStackTrace();
								}
							} else if (l.equals(f.lastModified())) {
								continue;
							} else {
								o.modifyTime = f.lastModified();
								log.debug("File : [" + f.getName() + "] changed");
								try {
									// for (FileTracer tracer : tracers) {
									o.tracer.fileChanged(f);
									// }
								} catch (Throwable t) {
									log.error(t.getMessage());
									t.printStackTrace();
								}
							}
						}
					}
				} catch (Throwable t) {
					log.error(t.getMessage());
					t.printStackTrace();
				} finally {
					if (removed != null) {
						for (FileListener e : removed) {
							files.remove(e);
						}
					}
					scaning = false;
					if (!tmpFiles.isEmpty()) {
						for (FileListener fl : tmpFiles) {
							if (!files.contains(fl)) {
								files.add(fl);
							}
						}
						tmpFiles.clear();
					}
				}
			}
		}
	}


	private static final Scaner scaner = new Scaner();

	static {
		new Timer().schedule(scaner, 0, 2000);
	}

	final FileChangeListener listener;// new ArrayList<FileChangeListener>();

	public FileTracer(FileChangeListener listener) {
		super();
		this.listener = listener;
	}

	public void addFile(File f) {
		scaner.addFile(this, f);
	}

	public void addFile(String f) {
		addFile(new File(f));
	}

	public void fileChanged(File f) {
		listener.fileChanged(f);
	}

	public void fileDeleted(File f) {
		listener.fileDeleted(f);
	}

}
