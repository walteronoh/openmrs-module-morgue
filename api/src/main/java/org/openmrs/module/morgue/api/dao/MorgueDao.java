/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.morgue.api.dao;

import org.hibernate.criterion.Restrictions;
import org.openmrs.Patient;
import org.openmrs.api.db.hibernate.DbSession;
import org.openmrs.api.db.hibernate.DbSessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import java.util.Date;
import java.util.List;
import org.openmrs.Person;
import org.openmrs.PersonName;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import javax.persistence.criteria.Join;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hibernate.CacheMode;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Projections;
import org.hibernate.exception.DataException;
import org.hibernate.transform.Transformers;
import org.openmrs.Cohort;
import org.openmrs.Order;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.DAOException;
import org.openmrs.util.PrivilegeConstants;

import javax.persistence.EntityManager;

// @Repository("morgue.MorgueDao")
public class MorgueDao {
	
	private SessionFactory sessionFactory;
	
	/**
	 * @Autowired private LabOrderManifestDao labOrderManifestDao;
	 */
	/**
	 * @param sessionFactory the sessionFactory to set
	 */
	public void setSessionFactory(SessionFactory sessionFactory) {
		this.sessionFactory = sessionFactory;
	}
	
	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}
	
	/**
	 * Get patients // SELECT p.patient_id, pr.person_id, pn.person_name_id, pn.given_name,
	 * pn.middle_name, pn.family_name, pn.preferred FROM patient p JOIN person pr ON p.patient_id =
	 * pr.person_id LEFT JOIN person_name pn ON pr.person_id = pn.person_id WHERE pn.voided=0 and
	 * pr.voided=0 and (pn.given_name like "%john%" or pn.middle_name like "%john%" or
	 * pn.family_name like "%john%");
	 * 
	 * @param dead
	 * @param name
	 * @param uuid
	 * @param createdOnOrAfterDate
	 * @param createdOnOrBeforeDate
	 * @param locationUuid
	 * @return
	 */
	public List<Object[]> getPatients(String dead, String name, String uuid, Date createdOnOrAfterDate,
			Date createdOnOrBeforeDate, String locationUuid) {
		System.err.println("Morgue Searching for patients using: " + uuid + " : " + name + " : " + dead + " : "
				+ createdOnOrAfterDate + " : " + createdOnOrBeforeDate + " : " + locationUuid + " : ");

		Session session = this.sessionFactory.getCurrentSession();

		// Build native SQL query with CAST to CHAR for dates to avoid JDBC driver
		// crashes on invalid dates
		StringBuilder sql = new StringBuilder();
		sql.append(
				"SELECT p.patient_id, p.creator, CAST(p.date_created AS CHAR), p.changed_by, CAST(p.date_changed AS CHAR), p.voided, ");
		sql.append("p.voided_by, CAST(p.date_voided AS CHAR), p.void_reason, pr.uuid, p.allergy_status, ");
		sql.append(
				"pr.person_id, pr.gender, CAST(pr.birthdate AS CHAR), pr.birthdate_estimated, pr.dead, CAST(pr.death_date AS CHAR), ");
		sql.append(
				"pr.cause_of_death, pr.creator AS person_creator, CAST(pr.date_created AS CHAR) AS person_date_created, ");
		sql.append("pr.changed_by AS person_changed_by, CAST(pr.date_changed AS CHAR) AS person_date_changed, ");
		sql.append("pr.voided AS person_voided, pr.voided_by AS person_voided_by, ");
		sql.append("CAST(pr.date_voided AS CHAR) AS person_date_voided, pr.void_reason AS person_void_reason, ");
		sql.append("pr.uuid AS person_uuid, pr.deathdate_estimated, CAST(pr.birthtime AS CHAR), ");
		sql.append("pn.person_name_id, pn.preferred, pn.person_id AS pn_person_id, pn.prefix, ");
		sql.append("pn.given_name, pn.middle_name, pn.family_name_prefix, pn.family_name, ");
		sql.append("pn.family_name2, pn.family_name_suffix, pn.degree, pn.creator AS pn_creator, ");
		sql.append(
				"CAST(pn.date_created AS CHAR) AS pn_date_created, pn.voided AS pn_voided, pn.voided_by AS pn_voided_by, ");
		sql.append(
				"CAST(pn.date_voided AS CHAR) AS pn_date_voided, pn.void_reason AS pn_void_reason, pn.uuid AS pn_uuid, ");
		sql.append("pn.changed_by AS pn_changed_by, CAST(pn.date_changed AS CHAR) AS pn_date_changed ");
		sql.append("FROM patient p ");
		sql.append("JOIN person pr ON p.patient_id = pr.person_id ");
		sql.append("LEFT JOIN person_name pn ON pr.person_id = pn.person_id ");
		sql.append("LEFT JOIN encounter e ON e.patient_id = p.patient_id ");
		sql.append("LEFT JOIN encounter_type et ON et.encounter_type_id = e.encounter_type ");
		sql.append("LEFT JOIN location l on l.location_id = e.location_id ");
		sql.append("WHERE pn.voided = 0 AND pr.voided = 0 AND et.encounter_type_id in (21, 31, 116) ");

		// Add filters
		if (dead != null && !dead.isEmpty()) {
			if (dead.trim().equalsIgnoreCase("true")) {
				sql.append("AND pr.dead = 1 ");
			} else {
				sql.append("AND pr.dead = 0 ");
			}
		}

		if (uuid != null && !uuid.isEmpty()) {
			sql.append("AND pr.uuid = :uuid ");
		}

		if (name != null && !name.isEmpty()) {
			sql.append("AND (pn.given_name LIKE :name OR pn.middle_name LIKE :name OR pn.family_name LIKE :name) ");
		}

		if (createdOnOrAfterDate != null) {
			sql.append("AND p.date_created >= :createdAfter ");
		}

		if (createdOnOrBeforeDate != null) {
			sql.append("AND p.date_created <= :createdBefore ");
		}

		if (locationUuid != null && !locationUuid.isEmpty()) {
			sql.append("AND l.uuid = :locationUuid ");
		}

		sql.append("ORDER BY p.patient_id");

		// System.out.println("Generated SQL Query: " + sql.toString());

		// Create native query
		org.hibernate.query.NativeQuery query = session.createNativeQuery(sql.toString());

		// Set parameters
		if (uuid != null && !uuid.isEmpty()) {
			query.setParameter("uuid", uuid);
		}

		if (name != null && !name.isEmpty()) {
			String searchPattern = "%" + name.trim().toLowerCase() + "%";
			query.setParameter("name", searchPattern);
		}

		if (createdOnOrAfterDate != null) {
			query.setParameter("createdAfter", createdOnOrAfterDate);
		}

		if (createdOnOrBeforeDate != null) {
			query.setParameter("createdBefore", createdOnOrBeforeDate);
		}

		if (locationUuid != null) {
			query.setParameter("locationUuid", locationUuid);
		}

		List<Object[]> rawResults = query.list();
		List<Object[]> mappedResults = new ArrayList<>();

		// Date parser helper
		java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

		for (Object[] row : rawResults) {
			try {
				// Map Patient
				Patient patient = new Patient();
				patient.setPatientId((Integer) row[0]);
				// patient.setCreator(...); // Skipping complex user mapping for now as it
				// requires User object
				patient.setDateCreated(parseDateSafe(row[2], dateFormat));
				// patient.setChangedBy(...);
				patient.setDateChanged(parseDateSafe(row[4], dateFormat));
				patient.setVoided(getBoolean(row[5]));
				// patient.setVoidedBy(...);
				patient.setDateVoided(parseDateSafe(row[7], dateFormat));
				patient.setVoidReason((String) row[8]);
				patient.setUuid((String) row[9]);
				// patient.setAllergyStatus((String) row[10]);

				// Map Person
				Person person = new Person();
				person.setPersonId((Integer) row[11]);
				person.setGender((String) row[12]);
				person.setBirthdate(parseDateSafe(row[13], dateFormat));
				person.setBirthdateEstimated(getBoolean(row[14]));
				person.setDead(getBoolean(row[15]));
				person.setDeathDate(parseDateSafe(row[16], dateFormat));
				// person.setCauseOfDeath(...); // Requires Concept
				// person.setCreator(...);
				person.setDateCreated(parseDateSafe(row[19], dateFormat));
				// person.setChangedBy(...);
				person.setDateChanged(parseDateSafe(row[21], dateFormat));
				person.setPersonVoided(getBoolean(row[22]));
				// person.setVoidedBy(...);
				person.setPersonDateVoided(parseDateSafe(row[24], dateFormat));
				person.setPersonVoidReason((String) row[25]);
				person.setUuid((String) row[26]);
				person.setDeathdateEstimated(getBoolean(row[27]));
				// person.setBirthtime(parseDateSafe(row[28], dateFormat));

				// Map PersonName
				PersonName personName = new PersonName();
				personName.setPersonNameId((Integer) row[29]);
				personName.setPreferred(getBoolean(row[30]));
				personName.setPerson(person); // Link to person
				personName.setPrefix((String) row[32]);
				personName.setGivenName((String) row[33]);
				personName.setMiddleName((String) row[34]);
				personName.setFamilyNamePrefix((String) row[35]);
				personName.setFamilyName((String) row[36]);
				personName.setFamilyName2((String) row[37]);
				personName.setFamilyNameSuffix((String) row[38]);
				personName.setDegree((String) row[39]);
				// personName.setCreator(...);
				personName.setDateCreated(parseDateSafe(row[41], dateFormat));
				personName.setVoided(getBoolean(row[42]));
				// personName.setVoidedBy(...);
				personName.setDateVoided(parseDateSafe(row[44], dateFormat));
				personName.setVoidReason((String) row[45]);
				personName.setUuid((String) row[46]);
				// personName.setChangedBy(...);
				personName.setDateChanged(parseDateSafe(row[48], dateFormat));

				// Add PersonName to Person
				java.util.Set<PersonName> names = new java.util.HashSet<>();
				names.add(personName);
				person.setNames(names);

				// Also set Person on Patient (Patient extends Person)
				patient.setPersonId(person.getPersonId());
				patient.setGender(person.getGender());
				patient.setBirthdate(person.getBirthdate());
				patient.setDead(person.isDead());
				patient.setNames(names);

				// Construct result array matching MorguePatientResource expectation
				Object[] mappedRow = new Object[8];
				mappedRow[0] = patient;
				mappedRow[1] = person;
				mappedRow[2] = personName;
				mappedRow[3] = personName.getPersonNameId();
				mappedRow[4] = personName.getGivenName();
				mappedRow[5] = personName.getMiddleName();
				mappedRow[6] = personName.getFamilyName();
				mappedRow[7] = personName.getPreferred();

				mappedResults.add(mappedRow);

			} catch (Exception e) {
				System.err.println("Error mapping patient row: " + e.getMessage());
				e.printStackTrace();
			}
		}

		return mappedResults;
	}
	
	private Date parseDateSafe(Object dateObj, java.text.SimpleDateFormat format) {
		if (dateObj == null)
			return null;
		String dateStr = dateObj.toString();
		try {
			return format.parse(dateStr);
		}
		catch (Exception e) {
			try {
				return new java.text.SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
			}
			catch (Exception e2) {
				return null; // Return null for invalid dates instead of crashing
			}
		}
	}
	
	private Boolean getBoolean(Object val) {
		if (val == null)
			return false;
		if (val instanceof Boolean)
			return (Boolean) val;
		if (val instanceof Number)
			return ((Number) val).intValue() == 1;
		return "true".equalsIgnoreCase(val.toString());
	}
}
