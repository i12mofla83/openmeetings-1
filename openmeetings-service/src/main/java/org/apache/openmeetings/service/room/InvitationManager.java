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
package org.apache.openmeetings.service.room;

import static org.apache.openmeetings.util.OpenmeetingsVariables.webAppRootKey;
import static org.apache.openmeetings.util.OpenmeetingsVariables.wicketApplicationName;

import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.Vector;

import org.apache.openmeetings.core.IApplication;
import org.apache.openmeetings.core.mail.MailHandler;
import org.apache.openmeetings.core.mail.SMSHandler;
import org.apache.openmeetings.db.dao.basic.ConfigurationDao;
import org.apache.openmeetings.db.dao.label.FieldLanguagesValuesDao;
import org.apache.openmeetings.db.dao.room.IInvitationManager;
import org.apache.openmeetings.db.dao.room.InvitationDao;
import org.apache.openmeetings.db.entity.basic.MailMessage;
import org.apache.openmeetings.db.entity.calendar.Appointment;
import org.apache.openmeetings.db.entity.calendar.MeetingMember;
import org.apache.openmeetings.db.entity.room.Invitation;
import org.apache.openmeetings.db.entity.room.Invitation.MessageType;
import org.apache.openmeetings.db.entity.room.Invitation.Valid;
import org.apache.openmeetings.db.entity.room.Room;
import org.apache.openmeetings.db.entity.user.User;
import org.apache.openmeetings.db.entity.user.User.Type;
import org.apache.openmeetings.db.util.TimezoneUtil;
import org.apache.openmeetings.service.mail.template.AbstractAppointmentTemplate;
import org.apache.openmeetings.service.mail.template.CanceledAppointmentTemplate;
import org.apache.openmeetings.service.mail.template.CreatedAppointmentTemplate;
import org.apache.openmeetings.service.mail.template.InvitationTemplate;
import org.apache.openmeetings.service.mail.template.UpdatedAppointmentTemplate;
import org.apache.openmeetings.util.crypt.MD5;
import org.apache.openmeetings.util.crypt.ManageCryptStyle;
import org.apache.openmeetings.util.mail.IcalHandler;
import org.apache.wicket.Application;
import org.apache.wicket.util.string.Strings;
import org.red5.logging.Red5LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 
 * @author swagner
 * 
 */
public class InvitationManager implements IInvitationManager {
	private static final Logger log = Red5LoggerFactory.getLogger(InvitationManager.class, webAppRootKey);

	@Autowired
	private InvitationDao invitationDao;
	@Autowired
	private FieldLanguagesValuesDao langDao;
	@Autowired
	private MailHandler mailHandler;
	@Autowired
	private SMSHandler smsHandler;
	@Autowired
	private TimezoneUtil timezoneUtil;
	@Autowired
	private ConfigurationDao configDao;

	/**
	 * @author vasya
	 * 
     * @param mm
     * @param a
     * @param message
     * @param baseurl
     * @param subject
	 * @throws Exception 
	 */
	private void sendInvitionLink(Appointment a, MeetingMember mm, MessageType type, boolean ical) throws Exception	{
		User owner = a.getOwner();
		String invitorName = owner.getFirstname() + " " + owner.getLastname();
		Long langId = mm.getUser().getLanguageId();
		TimeZone tz = timezoneUtil.getTimeZone(mm.getUser());
		AbstractAppointmentTemplate t = null;
		switch (type) {
			case Cancel:
				t = new CanceledAppointmentTemplate(langId, a, tz, invitorName);
				break;
			case Create:
				t = new CreatedAppointmentTemplate(langId, a, tz, invitorName);
				break;
			case Update:
			default:
				t = new UpdatedAppointmentTemplate(langId, a, tz, invitorName);
				break;
			
		}
		sendInvitionLink(mm.getInvitation(), type, t.getSubject(), t.getEmail(), ical);
	}
	
	public void sendInvitionLink(Invitation i, MessageType type, String subject, String message, boolean ical) throws Exception {
		String invitation_link = type == MessageType.Cancel ? null : ((IApplication)Application.get(wicketApplicationName)).getOmInvitationLink(configDao.getBaseUrl(), i); //TODO check for exceptions
		User owner = i.getInvitedBy();
		
		String invitorName = owner.getFirstname() + " " + owner.getLastname();
		String template = new InvitationTemplate(i.getInvitee().getLanguageId(), invitorName, message, invitation_link).getEmail();
		String email = i.getInvitee().getAdresses().getEmail();
		String replyToEmail = owner.getAdresses().getEmail();
		
		if (ical) {
			String username = i.getInvitee().getLogin();
			boolean isOwner = owner.getId().equals(i.getInvitee().getId());
			IcalHandler handler = new IcalHandler(MessageType.Cancel == type ? IcalHandler.ICAL_METHOD_CANCEL : IcalHandler.ICAL_METHOD_REQUEST);

			HashMap<String, String> attendeeList = handler.getAttendeeData(email, username, isOwner);

			Vector<HashMap<String, String>> atts = new Vector<HashMap<String, String>>();
			atts.add(attendeeList);

			// Defining Organizer

			HashMap<String, String> organizerAttendee = handler.getAttendeeData(email, username, isOwner);
			organizerAttendee = handler.getAttendeeData(replyToEmail, owner.getLogin(), isOwner);

			Appointment a = i.getAppointment();
			// Create ICal Message
			//FIXME should be checked to generate valid time
			String meetingId = handler.addNewMeeting(a.getStart(), a.getEnd(),
					a.getTitle(), atts, invitation_link,
					organizerAttendee, a.getIcalId(), timezoneUtil.getTimeZone(owner));

			// Writing back meetingUid
			if (Strings.isEmpty(a.getIcalId())) {
				a.setIcalId(meetingId);
				// TODO should it be saved ???
			}

			log.debug(handler.getICalDataAsString());
			mailHandler.send(new MailMessage(email, replyToEmail, subject, template, handler.getIcalAsByteArray()));
		} else {
			mailHandler.send(email, replyToEmail, subject, template);
		}
	}

	/**
	 * This method sends invitation reminder SMS
	 * @param phone user's phone
	 * @param subject 
	 * @return
	 */
	public boolean sendInvitationReminderSMS(String phone, String subject, long language_id) {
		if (!Strings.isEmpty(phone)) {
			log.debug("sendInvitationReminderSMS to " + phone + ": " + subject);
			try {
				return smsHandler.sendSMS(phone, subject, language_id);
			} catch (Exception e) {
				log.error("sendInvitationReminderSMS", e);
			}
		}
		return false;
	}

	/**
	 * 
	 * @param hashCode
	 * @param hidePass
	 * @return
	 */
	public Object getInvitationByHashCode(String hashCode, boolean hidePass) {
		try {
			Invitation invitation = invitationDao.getInvitationByHashCode(hashCode, hidePass);

			if (invitation == null) {
				// already deleted or does not exist
				return new Long(-31);
			} else {
				switch (invitation.getValid()) {
					case OneTime:
						// do this only if the user tries to get the Invitation, not
						// while checking the PWD
						if (hidePass) {
							// one-time invitation
							if (invitation.isUsed()) {
								// Invitation is of type *only-one-time* and was
								// already used
								return new Long(-32);
							} else {
								// set to true if this is the first time / a normal
								// getInvitation-Query
								invitation.setUsed(true);
								invitationDao.update(invitation);
								// invitation.setInvitationpass(null);
								invitation.setAllowEntry(true);
							}
						} else {
							invitation.setAllowEntry(true);
						}
						break;
					case Period:
						TimeZone tz = timezoneUtil.getTimeZone(invitation.getInvitee());
						Calendar now = Calendar.getInstance(tz);
						Calendar start = Calendar.getInstance(tz);
						start.setTime(invitation.getValidFrom());

						Calendar end = Calendar.getInstance(tz);
						end.setTime(invitation.getValidTo());
						if (now.after(start) && now.before(end)) {
							invitationDao.update(invitation);
							// invitation.setInvitationpass(null);
							invitation.setAllowEntry(true);
						} else {

							// Invitation is of type *period* and is not valid
							// anymore, this is an extra hook to display the time
							// correctly
							// in the method where it shows that the hash code does
							// not work anymore
							invitation.setAllowEntry(false);
						}
						break;
					case Endless:
					default:
						invitationDao.update(invitation);

						invitation.setAllowEntry(true);
						// invitation.setInvitationpass(null);
						break;
				}
				return invitation;
			}

		} catch (Exception err) {
			log.error("[getInvitationByHashCode]", err);
		}
		return new Long(-1);
	}

	/**
	 * 
	 * @param hashCode
	 * @param pass
	 * @return
	 */
	public Object checkInvitationPass(String hashCode, String pass) {
		try {
			Object obj = getInvitationByHashCode(hashCode, false);
			log.debug("checkInvitationPass - obj: " + obj);
			if (obj instanceof Invitation) {
				Invitation invitation = (Invitation) obj;

				// log.debug("invitationId "+invitation.getInvitations_id());
				// log.debug("pass "+pass);
				// log.debug("getInvitationpass "+invitation.getInvitationpass());

				if (ManageCryptStyle.getInstanceOfCrypt().verifyPassword(pass, invitation.getPassword())) {
					return new Long(1);
				} else {
					return new Long(-34);
				}
			} else {
				return obj;
			}
		} catch (Exception ex2) {
			log.error("[checkInvitationPass] ", ex2);
		}
		return new Long(-1);
	}

	public void processInvitation(Appointment a, MeetingMember member, MessageType type) {
		processInvitation(a, member, type, true);
	}

	public void processInvitation(Appointment a, MeetingMember mm, MessageType type, boolean sendMail) {
		if (a.getRemind() == null) {
			log.error("Appointment doesn't have reminder set!");
			return;
		}
		long remindType = a.getRemind().getId();
		if (remindType < 2) {
			log.error("MeetingMember should not have invitation!");
			return;
		}
	
		log.debug(":::: processInvitation ..... " + remindType);
	
		// appointment.getRemind().getTypId() == 1 will not receive emails
		if (remindType > 1) {
			log.debug("Invitation for Appointment : simple email");
	
			try {
				mm.setInvitation(getInvitation(mm.getInvitation()
						, mm.getUser(), a.getRoom(), a.isPasswordProtected(), a.getPassword()
						, Valid.Period, a.getOwner(), null, a.getStart(), a.getEnd(), a));
				if (sendMail) {
					sendInvitionLink(a, mm, type, remindType > 2);
				}
			} catch (Exception e) {
				log.error("Unexpected error while setting invitation", e);
			}
		}
	}

	public Invitation getInvitation(Invitation _invitation, User inveetee, Room room
			, boolean isPasswordProtected, String invitationpass, Valid valid,
			User createdBy, Long language_id, Date gmtTimeStart, Date gmtTimeEnd
			, Appointment appointment) {
		
		Invitation invitation = _invitation;
		if (null == _invitation) {
			invitation = new Invitation();
			String hashRaw = "HASH" + (System.currentTimeMillis());
			try {
				invitation.setHash(MD5.do_checksum(hashRaw));
			} catch (NoSuchAlgorithmException e) {
				log.error("Unexpected error while creating invitation", e);
				throw new RuntimeException(e);
			}
		}
	
		invitation.setPasswordProtected(isPasswordProtected);
		if (isPasswordProtected) {
			invitation.setPassword(ManageCryptStyle.getInstanceOfCrypt().createPassPhrase(invitationpass));
		}
	
		invitation.setUsed(false);
		invitation.setValid(valid);
		
		// valid period of Invitation
		switch (valid) {
			case Period:
				invitation.setValidFrom(new Date(gmtTimeStart.getTime() - (5 * 60 * 1000)));
				invitation.setValidTo(gmtTimeEnd);
				break;
			case Endless:
			case OneTime:
			default:
				break;
		}
	
		invitation.setDeleted(false);
	
		invitation.setInvitedBy(createdBy);
		invitation.setInvitee(inveetee);
		if (language_id != null && Type.contact == invitation.getInvitee().getType()) {
			invitation.getInvitee().setLanguageId(language_id);
		}
		invitation.setRoom(room);
		invitation.setInserted(new Date());
		invitation.setAppointment(appointment);
	
		return invitation;
	}

	public Invitation getInvitation(User inveetee, Room room, boolean isPasswordProtected, String invitationpass, Valid valid,
			User createdBy, Long language_id, Date gmtTimeStart, Date gmtTimeEnd, Appointment appointment)
	{
		Invitation i = getInvitation((Invitation)null, inveetee, room, isPasswordProtected, invitationpass, valid, createdBy
				, language_id, gmtTimeStart, gmtTimeEnd, appointment);
		i = invitationDao.update(i);
		return i;
	}

	
}