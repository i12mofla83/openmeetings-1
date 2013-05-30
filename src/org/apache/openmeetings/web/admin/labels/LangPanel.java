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
package org.apache.openmeetings.web.admin.labels;

import static org.apache.openmeetings.OpenmeetingsVariables.webAppRootKey;
import static org.apache.openmeetings.web.app.Application.getBean;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.List;

import org.apache.openmeetings.data.basic.FieldLanguageDao;
import org.apache.openmeetings.data.basic.FieldManager;
import org.apache.openmeetings.data.basic.FieldValueDao;
import org.apache.openmeetings.persistence.beans.lang.FieldLanguage;
import org.apache.openmeetings.persistence.beans.lang.Fieldlanguagesvalues;
import org.apache.openmeetings.persistence.beans.lang.Fieldvalues;
import org.apache.openmeetings.servlet.outputhandler.ImportController;
import org.apache.openmeetings.servlet.outputhandler.LangExport;
import org.apache.openmeetings.web.admin.AdminPanel;
import org.apache.openmeetings.web.admin.SearchableDataView;
import org.apache.openmeetings.web.common.PagedEntityListPanel;
import org.apache.openmeetings.web.data.DataViewContainer;
import org.apache.openmeetings.web.data.OrderByBorder;
import org.apache.openmeetings.web.data.SearchableDataProvider;
import org.apache.openmeetings.web.util.AjaxDownload;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.ajax.AjaxEventBehavior;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.form.AjaxFormSubmitBehavior;
import org.apache.wicket.extensions.ajax.markup.html.form.upload.UploadProgressBar;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.upload.FileUpload;
import org.apache.wicket.markup.html.form.upload.FileUploadField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.markup.repeater.Item;
import org.apache.wicket.util.resource.AbstractResourceStream;
import org.apache.wicket.util.resource.ResourceStreamNotFoundException;
import org.dom4j.Document;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;

import com.googlecode.wicket.jquery.ui.form.button.AjaxButton;

/**
 * Language Editor, add/insert/update {@link Fieldlanguagesvalues} and
 * add/delete {@link FieldLanguage} contains several Forms and one list
 * 
 * @author solomax, swagner
 * 
 */
public class LangPanel extends AdminPanel {
	private static final Logger log = Red5LoggerFactory.getLogger(LangPanel.class, webAppRootKey);
	
	private static final long serialVersionUID = 5904180813198016592L;

	FieldLanguage language;
	final WebMarkupContainer listContainer;
	private LangForm langForm;
	private FileUploadField fileUploadField;
	// Create feedback panels
	private final FeedbackPanel importFeedback = new FeedbackPanel("importFeedback");
	
	@Override
	public void onMenuPanelLoad(AjaxRequestTarget target) {
		target.appendJavaScript("labelsInit();");
	}

	@SuppressWarnings("unchecked")
	public LangPanel(String id) {
		super(id);
		// Create feedback panels
		add(importFeedback.setOutputMarkupId(true));
		FieldLanguageDao langDao = getBean(FieldLanguageDao.class);
		language = langDao.getFieldLanguageById(1L);

		Fieldlanguagesvalues flv = new Fieldlanguagesvalues();
		flv.setLanguage_id(language.getLanguage_id());
		final LabelsForm form = new LabelsForm("form", this, flv);
		form.showNewRecord();
		add(form);

		final SearchableDataView<Fieldvalues> dataView = new SearchableDataView<Fieldvalues>(
				"langList"
				, new SearchableDataProvider<Fieldvalues>(FieldValueDao.class) {
					private static final long serialVersionUID = -6822789354860988626L;

					@Override
					protected FieldValueDao getDao() {
						return (FieldValueDao)super.getDao();
					}
					
					@Override
					public long size() {
						return search == null ? getDao().count() : getDao().count(language.getLanguage_id(), search);
					}
					
					public Iterator<? extends Fieldvalues> iterator(long first, long count) {
						return (search == null && getSort() == null
								? getDao().get(language.getLanguage_id(), (int)first, (int)count)
								: getDao().get(language.getLanguage_id(), search, (int)first, (int)count, getSortStr())).iterator();
					}
				}) {
			private static final long serialVersionUID = 8715559628755439596L;

			@Override
			protected void populateItem(final Item<Fieldvalues> item) {
				final Fieldvalues fv = item.getModelObject();
				item.add(new Label("lblId", "" + fv.getFieldvalues_id()));
				item.add(new Label("name", fv.getName()));
				item.add(new Label("value", fv.getFieldlanguagesvalue() != null ? fv.getFieldlanguagesvalue().getValue() : null));
				item.add(new AjaxEventBehavior("onclick") {
					private static final long serialVersionUID = -8069413566800571061L;

					protected void onEvent(AjaxRequestTarget target) {
						form.setModelObject(fv.getFieldlanguagesvalue());
						form.hideNewRecord();
						target.add(form);
						target.appendJavaScript("labelsInit();");
					}
				});
				item.add(AttributeModifier.append("class", "clickable "
						+ ((item.getIndex() % 2 == 1) ? "even" : "odd")));
			}
		};

		listContainer = new WebMarkupContainer("listContainer");
		add(listContainer.add(dataView).setOutputMarkupId(true));
		DataViewContainer<Fieldvalues> container = new DataViewContainer<Fieldvalues>(listContainer, dataView);
		container.setLinks(new OrderByBorder<Fieldvalues>("orderById", "fieldvalues.fieldvalues_id", container)
				, new OrderByBorder<Fieldvalues>("orderByName", "fieldvalues.name", container)
				, new OrderByBorder<Fieldvalues>("orderByValue", "value", container));
		add(container.orderLinks);
		add(new PagedEntityListPanel("navigator", dataView) {
			private static final long serialVersionUID = 5097048616003411362L;

			@Override
			protected void onEvent(AjaxRequestTarget target) {
				dataView.modelChanging();
				target.add(listContainer);
			}
		});
		langForm = new LangForm("langForm", listContainer, this);
		fileUploadField = new FileUploadField("fileInput");
		langForm.add(fileUploadField);
		langForm.add(new UploadProgressBar("progress", langForm, fileUploadField));
		fileUploadField.add(new AjaxFormSubmitBehavior(langForm, "onchange") {
			private static final long serialVersionUID = 2160216679027859231L;

			@Override
			protected void onSubmit(AjaxRequestTarget target) {
				FileUpload download = fileUploadField.getFileUpload();
				try {
					if (download == null || download.getInputStream() == null) {
						importFeedback.error("File is empty");
						return;
					}
					getBean(ImportController.class).importLanguage(language.getLanguage_id(), download.getInputStream());
				} catch (IOException e) {
					log.error("IOException on panel language editor import ", e);
					importFeedback.error(e);
				} catch (Exception e) {
					log.error("Exception on panel language editor import ", e);
					importFeedback.error(e);
				}

				// repaint the feedback panel so that it is hidden
				target.add(importFeedback);
			}
		});

		// Add a component to download a file without page refresh
		final AjaxDownload download = new AjaxDownload();
		langForm.add(download);

		langForm.add(new AjaxButton("export"){
			private static final long serialVersionUID = -2845639751469777460L;

			protected void onSubmit(AjaxRequestTarget target, Form<?> form) {

				final List<Fieldlanguagesvalues> flvList = getBean(FieldManager.class).getMixedFieldValuesList(language.getLanguage_id());

				FieldLanguage fl = getBean(FieldLanguageDao.class).getFieldLanguageById(language.getLanguage_id());
				if (fl != null && flvList != null) {
					download.setFileName(fl.getName() + ".xml");
					download.setResourceStream(new AbstractResourceStream() {
						private static final long serialVersionUID = 1L;
						private StringWriter sw;
						private InputStream is;
						
						public InputStream getInputStream() throws ResourceStreamNotFoundException {
							try {
								Document doc = LangExport.createDocument(flvList, getBean(FieldManager.class).getUntranslatedFieldValuesList(language.getLanguage_id()));
								sw = new StringWriter();
								LangExport.serializetoXML(sw, "UTF-8", doc);
								is = new ByteArrayInputStream(sw.toString().getBytes());
								return is;
							} catch (Exception e) {
								throw new ResourceStreamNotFoundException(e);
							}
						}
						
						public void close() throws IOException {
							if (is != null) {
								is.close();
								is = null;
							}
							sw = null;
						}
					});//new FileResourceStream(new File(requestedFile)));
					download.initiate(target);
				}
				
				// repaint the feedback panel so that it is hidden
				target.add(importFeedback);
			}
			
			@Override
			protected void onError(AjaxRequestTarget target, Form<?> form) {
				// repaint the feedback panel so errors are shown
				target.add(importFeedback);
			}
			
		});
		
		add(langForm);
		add(new AddLanguageForm("addLangForm", this));
	}

	public LangForm getLangForm() {
		return langForm;
	}
}
