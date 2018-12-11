package it.alexincerti.models;

import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.voodoodyne.jackson.jsog.JSOGGenerator;

@JsonIdentityInfo(generator = JSOGGenerator.class)
@RelationshipEntity(type = "IN_CATEGORY")
public class ContainerCategoryRelationship {

	@Id
	@GeneratedValue
	Long id;

	@StartNode
	private Category childCategory;

	@EndNode
	private Category containerCategory;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Category getChildCategory() {
		return childCategory;
	}

	public void setChildCategory(Category childCategory) {
		this.childCategory = childCategory;
	}

	public Category getContainerCategory() {
		return containerCategory;
	}

	public void setContainerCategory(Category containerCategory) {
		this.containerCategory = containerCategory;
	}

}