/*
********************************************************************************
* Copyright (c) 9th November 2018 Cloudreach Limited Europe
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0.
*
* This Source Code may also be made available under the following Secondary
* Licenses when the conditions for such availability set forth in the Eclipse
* Public License, v. 2.0 are satisfied: GNU General Public License, version 2
* with the GNU Classpath Exception which is
* available at https://www.gnu.org/software/classpath/license.html.
*
* SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
********************************************************************************/
package com.cloudreach.connect.x2.sys.auth;

import com.cloudreach.connect.x2.internal.model.SystemDBObject;
import com.cloudreach.connect.x2.sys.internal.SystemDB;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.stream.Collectors;
import javax.xml.ws.Holder;

/**
 * this object represents a X2 user
 * 
 * @author christopher stura
 */
public class CCUser implements SystemDBObject {
	private String username = null;
	private String password = null;
	private List<CCGroup> groups = null;
	private boolean admin = false;
	private boolean groupsExtracted = false;

	public boolean isAdmin() {
		return admin;
	}

	public void setAdmin(boolean admin) {
		this.admin = admin;
	}
	
	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	/**
	 * please note this is not the actual user password as this really represents the UUID hash of the user's password.
	 * we don't store the actual user password on the system.
	 * 
	 * @return the UUID hash of the user password stored on the system.
	 */
	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	@JsonIgnore
	public List<CCGroup> getGroups() {
		return groups;
	}

	@JsonIgnore
	public void setGroups(List<CCGroup> groups) {
		this.groups = groups;
	}
	
	@JsonProperty(value = "groups")
	public List<String> getGroupIds() {
		if(groups != null) {
			return groups.stream().map(CCGroup::getId).collect(Collectors.toList());
		}
		
		return null;
	}
	
	@JsonProperty(value = "groups")
	public void setGroupIds(List<String> groupIds) {
		if(groupIds != null) {
			setGroups(groupIds.stream().map((gid) -> {
				CCGroup g = new CCGroup();
				g.setId(gid);
				return g;
			}).collect(Collectors.toList()));
		}
	}

	@Override
	@JsonIgnore
	public String getId() {
		return getUsername();
	}
	
	/**
	 * this method will copy all values which are null in this object with values that are in the source object
	 * @param user the source user object.
	 */
	@JsonIgnore
	public void merge(CCUser user) {
		if(this.username == null) {
			this.username = user.getUsername();
		}
		if(this.password == null) {
			this.password = user.getPassword();
		}
		if(this.groups == null) {
			this.groups = user.getGroups();
		}
	}
	
	@JsonIgnore
	public void extractGroups() {
		if(!groupsExtracted) {
			List<CCGroup> groupList = SystemDB.query(CCAuthentication.TABLE_GROUPS, CCGroup.class, getGroupIds().toArray(new String[] {}));
			setGroups(groupList);
			groupsExtracted = true;
		}
	}
	
	/**
	 * this method will calculate whether this user can create users in the specified target groups.
	 * @return true if a user can be created in the groups specified or not.
	 */
	@JsonIgnore
	public boolean canCreateUser(CCUser newUser) {
		if(isAdmin()) {
			return true; //the global administrator always can.
		} else if(newUser.isAdmin()) {
			return false;
		}
		
		//we need to extract the details of the groups associated to this new user and check.
		newUser.extractGroups();
		return newUser.getGroups().stream().anyMatch(g -> g.getAdminUsers() != null && g.getAdminUsers().contains(getUsername()));
 	}
	
	public boolean canCreateGroup(CCGroup newGroup) {
		return isAdmin();
	}
	
	public boolean canUpdateGroup(CCGroup newGroup) {
		return isAdmin() || (newGroup.getAdminUsers() != null && newGroup.getAdminUsers().contains(getUsername())); //if I am administrator of this group then I can modify it.
	}
	
	public boolean canDeleteGroup(CCGroup newGroup) {
		return isAdmin();
	}
	
	@JsonIgnore
	public boolean canUpdateUser(CCUser newUser) {
		if(isAdmin() || getUsername().equals(newUser.getUsername())) {
			return true;
		} else if(newUser.isAdmin()) {
			return false;
		}
		
		newUser.extractGroups();
		return !newUser.getGroups().stream().anyMatch(g -> g.getAdminUsers() == null || !g.getAdminUsers().contains(getUsername()));
	}
}
