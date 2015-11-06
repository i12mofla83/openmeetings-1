/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License") +  you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.openmeetings.core.data.file;

import static org.apache.openmeetings.util.OmFileHelper.thumbImagePrefix;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import org.apache.openmeetings.db.dao.file.FileExplorerItemDao;
import org.apache.openmeetings.db.entity.file.FileExplorerItem;
import org.apache.openmeetings.db.entity.file.FileItem.Type;
import org.apache.openmeetings.util.OmFileHelper;
import org.apache.openmeetings.util.OpenmeetingsVariables;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

public class FileUtils {
	private static final Logger log = Red5LoggerFactory.getLogger(
			FileProcessor.class, OpenmeetingsVariables.webAppRootKey);

	@Autowired
	private FileExplorerItemDao fileExplorerItemDao;

	public long getSizeOfDirectoryAndSubs(FileExplorerItem fileExplorerItem) {
		try {

			long fileSize = 0;

			File base = OmFileHelper.getUploadFilesDir();
			if (Type.Image == fileExplorerItem.getType()) {

				File tFile = new File(base, fileExplorerItem.getFileHash());
				if (tFile.exists()) {
					fileSize += tFile.length();
				}

				File thumbFile = new File(base, thumbImagePrefix + fileExplorerItem.getFileHash());
				if (thumbFile.exists()) {
					fileSize += thumbFile.length();
				}

			}

			if (Type.Presentation == fileExplorerItem.getType()) {

				File tFolder = new File(base, fileExplorerItem.getFileHash());

				if (tFolder.exists()) {
					fileSize += OmFileHelper.getSize(tFolder);
				}

			}

			log.debug("calling [1] FileExplorerItemDaoImpl.updateFileOrFolder()");
			fileExplorerItemDao.update(fileExplorerItem);

			List<FileExplorerItem> childElements = fileExplorerItemDao.getByParent(fileExplorerItem.getId());

			for (FileExplorerItem childExplorerItem : childElements) {

				fileSize += this.getSizeOfDirectoryAndSubs(childExplorerItem);

			}

			return fileSize;

		} catch (Exception err) {
			log.error("[getSizeOfDirectoryAndSubs] ", err);
		}
		return 0;
	}

	public void setFileToOwnerOrRoomByParent(FileExplorerItem fileExplorerItem,
			Long users_id, Long roomId) {
		try {

			fileExplorerItem.setOwnerId(users_id);
			fileExplorerItem.setRoomId(roomId);

			log.debug("calling [2] FileExplorerItemDaoImpl.updateFileOrFolder()");
			fileExplorerItemDao.update(fileExplorerItem);

			List<FileExplorerItem> childElements = fileExplorerItemDao.getByParent(fileExplorerItem.getId());

			for (FileExplorerItem childExplorerItem : childElements) {

				this.setFileToOwnerOrRoomByParent(childExplorerItem, users_id,
						roomId);

			}

		} catch (Exception err) {
			log.error("[setFileToOwnerOrRoomByParent] ", err);
		}
	}

	public String formatDate(Date date) {
		SimpleDateFormat formatter;
		String pattern = "dd/MM/yy H:mm:ss";
		Locale locale = new Locale("en", "US");
		formatter = new SimpleDateFormat(pattern, locale);
		return formatter.format(date);
	}
}
