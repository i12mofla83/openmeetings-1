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
package org.openmeetings.server.util;

import org.openmeetings.app.OpenmeetingsVariables;
import org.openmeetings.server.beans.ServerFrameBean;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;
/**
 * @author sebastianwagner
 *
 */
public class DePacketizerUtil {
	
	private static final Logger log = Red5LoggerFactory.getLogger(DePacketizerUtil.class, OpenmeetingsVariables.webAppRootKey);
	
	public ServerFrameBean handleReceivingBytes(byte[] incomingBytes) {
		try {
			
			log.debug("LENGTH incomingBytes "+incomingBytes.length);
			
			throw new Exception("This Class is deprecated use the Protocol Codec Filter");
		} catch (Exception err) {
			log.error("[handleReceivingBytes]",err);
		}
		return null;
	}
}
