package it.alexincerti.models;

import org.neo4j.ogm.annotation.EndNode;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.RelationshipEntity;
import org.neo4j.ogm.annotation.StartNode;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.voodoodyne.jackson.jsog.JSOGGenerator;

@JsonIdentityInfo(generator = JSOGGenerator.class)
@RelationshipEntity(type = "SUBCATEGORY_OF")
public class SubcategoryOfRelationship {

	@Id
	@GeneratedValue
	Long id;

	@StartNode
	private Category subCategory;

	@EndNode
	private Category upperCategory;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Category getSubCategory() {
		return subCategory;
	}

	public void setSubCategory(Category subCategory) {
		this.subCategory = subCategory;
	}

	public Category getUpperCategory() {
		return upperCategory;
	}

	public void setUpperCategory(Category upperCategory) {
		this.upperCategory = upperCategory;
	}
}