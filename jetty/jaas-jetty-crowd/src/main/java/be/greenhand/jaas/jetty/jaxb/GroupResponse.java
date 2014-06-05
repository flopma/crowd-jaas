/*
 * JAAS Jetty Crowd
 * Copyright (C) 2014 Issa Gorissen
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package be.greenhand.jaas.jetty.jaxb;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="group")
public class GroupResponse {
	@XmlAttribute
	public String name;
	
	public Link link;
	public String description;
	public String type;
	public boolean active;
	public Attributes attributes;
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
		builder.append("GroupResponse [name=").append(name).append(", link=")
				.append(link).append(", description=").append(description)
				.append(", type=").append(type).append(", active=")
				.append(active).append(", attributes=").append(attributes)
				.append("]");
		return builder.toString();
	}
}
