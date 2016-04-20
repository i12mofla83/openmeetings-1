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
package org.apache.openmeetings.web.room.sidebar;

import static org.apache.openmeetings.web.app.Application.getBean;
import static org.apache.openmeetings.web.app.Application.getRoomUsers;
import static org.apache.openmeetings.web.app.WebSession.getUserId;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.openmeetings.db.dao.user.UserDao;
import org.apache.openmeetings.db.entity.room.Room.RoomElement;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.openmeetings.web.app.Client;
import org.apache.openmeetings.web.app.Client.Right;
import org.apache.openmeetings.web.room.RoomPanel;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.core.request.handler.IPartialPageRequestHandler;
import org.apache.wicket.extensions.markup.html.tabs.ITab;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import com.googlecode.wicket.jquery.ui.widget.tabs.TabbedPanel;

public class RoomSidebar extends Panel {
	private static final long serialVersionUID = 1L;
	private final RoomPanel room;
	private final TabbedPanel tabs;
	private final ITab userTab;
	private final ITab fileTab;
	private final UploadDialog upload;
	private final RoomFilePanel roomFiles;
	private boolean showFiles;
	private final ListView<RoomClient> users = new ListView<RoomClient>("user", new ArrayList<RoomClient>()) {
		private static final long serialVersionUID = 1L;

		@Override
		protected void populateItem(ListItem<RoomClient> item) {
			RoomClient rc = item.getModelObject();
			item.setMarkupId(String.format("user%s", rc.c.getUid()));
			String status = null, statusTitle = null;
			if (rc.c.hasRight(Right.moderator)) {
				status = "status-mod";
				statusTitle = "679";
			} else if (rc.c.hasRight(Right.whiteBoard)) {
				status = "status-wb";
				statusTitle = "678";
			} else {
				status = "status-user";
				statusTitle = "677";
			}
			item.add(new WebMarkupContainer("status").add(AttributeAppender.append("class", status), AttributeAppender.replace("title", getString(statusTitle))));
			item.add(new Label("name", rc.u.getFirstname() + " " + rc.u.getLastname()));
			item.add(AttributeAppender.append("data-userid", rc.u.getId()));
			WebMarkupContainer actions = new WebMarkupContainer("actions");
			actions.add(new WebMarkupContainer("privateChat").setVisible(!room.getRoom().isHidden(RoomElement.Chat) && !getUserId().equals(rc.u.getId())));
			if (room.getClient() != null) {
				actions.setVisible(room.getClient().hasRight(Right.moderator));
				if (rc.c.getUid().equals(room.getClient().getUid())) {
					item.add(AttributeAppender.append("class", "current"));
				}
			} else {
				actions.setVisible(false);
			}
			item.add(actions);
		}
	};
	
	public RoomSidebar(String id, final RoomPanel room) {
		super(id);
		this.room = room;
		updateShowFiles();
		
		userTab = new ITab() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isVisible() {
				return true;
			}
			
			@Override
			public IModel<String> getTitle() {
				return Model.of(getString("613"));
			}
			
			@Override
			public WebMarkupContainer getPanel(String containerId) {
				return new UserFragment(containerId, "user-panel");
			}
		};
		fileTab = new ITab() {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isVisible() {
				return showFiles;
			}
			
			@Override
			public IModel<String> getTitle() {
				return Model.of(getString("614"));
			}
			
			@Override
			public WebMarkupContainer getPanel(String containerId) {
				return new FileFragment(containerId, "file-panel");
			}
		};
		add(tabs = new TabbedPanel("tabs", Arrays.asList(userTab, fileTab)).setActiveTab(room.getRoom().isFilesOpened() ? 1 : 0));
		roomFiles = new RoomFilePanel("tree", room);
		add(upload = new UploadDialog("upload", room, roomFiles));
	}
	
	public class UserFragment extends Fragment {
		private static final long serialVersionUID = 1L;

		public UserFragment(String id, String markupId) {
			super(id, markupId, RoomSidebar.this);
			add(users.setList(getUsers()));
		}
	}
	
	public class FileFragment extends Fragment {
		private static final long serialVersionUID = 1L;

		public FileFragment(String id, String markupId) {
			super(id, markupId, RoomSidebar.this);
			add(roomFiles);
		}
	}

	private List<RoomClient> getUsers() {
		List<RoomClient> list = new ArrayList<>();
		for (Client cl : getRoomUsers(room.getRoom().getId())) {
			list.add(new RoomClient(cl));
		}
		return list;
	}
	
	static class RoomClient implements Serializable {
		private static final long serialVersionUID = 1L;
		private final Client c;
		private final User u;
		
		RoomClient(Client c) {
			this.c = c;
			this.u = getBean(UserDao.class).get(c.getUserId());
		}
	}
	
	private void updateShowFiles() {
		showFiles = !room.getRoom().isHidden(RoomElement.Files) && room.getClient().hasRight(Right.whiteBoard);
	}
	
	public void updateUsers(IPartialPageRequestHandler handler) {
		updateShowFiles();
		users.setList(getUsers());
		handler.add(tabs);
	}

	public boolean isShowFiles() {
		return showFiles;
	}
	
	public void showUpload(IPartialPageRequestHandler handler) {
		upload.open(handler);
	}
}
