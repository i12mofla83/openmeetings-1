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
package org.openmeetings.app.documents;

import java.io.File;
import java.util.HashMap;

import org.openmeetings.app.OpenmeetingsVariables;
import org.openmeetings.app.data.basic.Configurationmanagement;
import org.openmeetings.app.data.user.dao.UsersDaoImpl;
import org.openmeetings.app.persistence.beans.user.Users;
import org.openmeetings.app.remote.red5.ScopeApplicationAdapter;
import org.openmeetings.utils.ProcessHelper;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

public class GenerateImage {

	private static final Logger log = Red5LoggerFactory.getLogger(
			GenerateImage.class, OpenmeetingsVariables.webAppRootKey);

	@Autowired
	private Configurationmanagement cfgManagement;
	@Autowired
	private UsersDaoImpl usersDao;
	@Autowired
	private GenerateThumbs generateThumbs;

	String getPathToImageMagic() {
		String pathToImageMagic = cfgManagement.getConfKey(3,
				"imagemagick_path").getConf_value();
		if (!pathToImageMagic.equals("")
				&& !pathToImageMagic.endsWith(File.separator)) {
			pathToImageMagic += File.separator;
		}
		pathToImageMagic += "convert" + GenerateSWF.execExt;
		return pathToImageMagic;
	}

	public HashMap<String, HashMap<String, String>> convertImage(
			String current_dir, String fileName, String fileExt,
			String roomName, String fileNameShort, boolean fullProcessing)
			throws Exception {

		HashMap<String, HashMap<String, String>> returnMap = new HashMap<String, HashMap<String, String>>();

		String working_imgdir = current_dir + OpenmeetingsVariables.UPLOAD_DIR + File.separatorChar
				+ roomName + File.separatorChar;
		String working_pptdir = current_dir + OpenmeetingsVariables.UPLOAD_TEMP_DIR + File.separatorChar
				+ roomName + File.separatorChar;

		String fileFullPath = working_pptdir + fileName + fileExt;

		File f = new File(working_imgdir + fileName + fileExt);
		if (f.exists()) {
			int recursiveNumber = 0;
			String tempd = fileName + "_" + recursiveNumber;
			while (f.exists()) {
				recursiveNumber++;
				tempd = fileName + "_" + recursiveNumber;
				f = new File(working_imgdir + tempd + fileExt);
			}
			fileName = tempd;
		}

		String destinationFile = working_imgdir + fileName;

		log.debug("##### convertImage destinationFile: " + destinationFile);

		HashMap<String, String> processJPG = this.convertSingleJpg(
				fileFullPath, destinationFile);
		HashMap<String, String> processThumb = generateThumbs.generateThumb(
				"_thumb_", current_dir, destinationFile, 50);

		returnMap.put("processJPG", processJPG);
		returnMap.put("processThumb", processThumb);

		// Delete old one
		File fToDelete = new File(fileFullPath);
		fToDelete.delete();

		return returnMap;
	}

	public HashMap<String, HashMap<String, String>> convertImageUserProfile(
			String current_dir, String fileName, String fileExt, Long users_id,
			String fileNameShort, boolean fullProcessing) throws Exception {

		HashMap<String, HashMap<String, String>> returnMap = new HashMap<String, HashMap<String, String>>();

		String working_imgdir = current_dir + OpenmeetingsVariables.UPLOAD_DIR + File.separatorChar
				+ "profiles" + File.separatorChar
				+ ScopeApplicationAdapter.profilesPrefix + users_id
				+ File.separatorChar;
		String working_pptdir = current_dir + OpenmeetingsVariables.UPLOAD_TEMP_DIR + File.separatorChar
				+ "profiles" + File.separatorChar
				+ ScopeApplicationAdapter.profilesPrefix + users_id
				+ File.separatorChar;

		String fileFullPath = working_pptdir + fileName + fileExt;

		File f = new File(working_imgdir + fileName + fileExt);
		if (f.exists()) {
			int recursiveNumber = 0;
			String tempd = fileName + "_" + recursiveNumber;
			while (f.exists()) {
				recursiveNumber++;
				tempd = fileName + "_" + recursiveNumber;
				f = new File(working_imgdir + tempd + fileExt);
			}
			fileName = tempd;
		}

		String destinationFile = working_imgdir + fileName;
		HashMap<String, String> processJPG = this.convertSingleJpg(
				fileFullPath, destinationFile);

		HashMap<String, String> processThumb1 = generateThumbs.generateThumb(
				"_chat_", current_dir, destinationFile, 40);
		HashMap<String, String> processThumb2 = generateThumbs.generateThumb(
				"_profile_", current_dir, destinationFile, 126);
		HashMap<String, String> processThumb3 = generateThumbs.generateThumb(
				"_big_", current_dir, destinationFile, 240);

		returnMap.put("processJPG", processJPG);
		returnMap.put("processThumb1", processThumb1);
		returnMap.put("processThumb2", processThumb2);
		returnMap.put("processThumb3", processThumb3);

		// Delete old one
		File fToDelete = new File(fileFullPath);
		fToDelete.delete();

		File fileNameToStore = new File(destinationFile + ".jpg");
		String pictureuri = fileNameToStore.getName();
		Users us = usersDao.getUser(users_id);
		us.setUpdatetime(new java.util.Date());
		us.setPictureuri(pictureuri);
		usersDao.updateUser(us);

		//FIXME: After uploading a new picture all other clients should refresh
		//scopeApplicationAdapter.updateUserSessionObject(users_id, pictureuri);

		return returnMap;
	}

	/**
	 * -density 150 -resize 800
	 * 
	 */
	private HashMap<String, String> convertSingleJpg(String inputFile,
			String outputfile) {
		String[] argv = new String[] { getPathToImageMagic(), inputFile,
				outputfile + ".jpg" };

		// return GenerateSWF.executeScript("convertSingleJpg", argv);

		if (System.getProperty("os.name").toUpperCase().indexOf("WINDOWS") == -1) {
			return ProcessHelper.executeScript("generateBatchThumbByWidth", argv);
		} else {
			return generateThumbs.processImageWindows(argv);
		}

	}

	public HashMap<String, String> convertImageByTypeAndSize(String inputFile,
			String outputfile, int width, int height) {
		String[] argv = new String[] { getPathToImageMagic(), "-size",
				width + "x" + height, inputFile, outputfile };
		return ProcessHelper.executeScript("convertImageByTypeAndSizeAndDepth",
				argv);
	}

	public HashMap<String, String> convertImageByTypeAndSizeAndDepth(
			String inputFile, String outputfile, int width, int height,
			int depth) {
		String[] argv = new String[] { getPathToImageMagic(), "-size",
				width + "x" + height, "-depth", Integer.toString(depth),
				inputFile, outputfile };
		return ProcessHelper.executeScript("convertImageByTypeAndSizeAndDepth",
				argv);
	}

}
