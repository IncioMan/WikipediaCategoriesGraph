package it.alexincerti.models;

import java.util.ArrayList;
import java.util.List;

import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
public class Category {

	@Id
	@GeneratedValue
	Long id;

	@Index(unique = true)
	private String name;

	@Relationship(type = DBSchemaProperties.SUBCATEGORY_RELATIONSHIP, direction = Relationship.UNDIRECTED)
	private List<SubcategoryOfRelationship> subcategoriesOf;

	@Relationship(type = DBSchemaProperties.IN_CATEGORY_RELATIONSHIP, direction = Relationship.UNDIRECTED)
	private List<ContainerCategoryRelationship> inCategories;

	public Category() {
		// TODO Auto-generated constructor stub
	}

	public Category(String name) {
		super();
		this.name = name;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<SubcategoryOfRelationship> getSubcategoriesOf() {
		if (subcategoriesOf == null) {
			subcategoriesOf = new ArrayList<>();
		}
		return subcategoriesOf;
	}

	public void setSubcategoriesOf(List<SubcategoryOfRelationship> subcategoriesOf) {
		this.subcategoriesOf = subcategoriesOf;
	}

	public List<ContainerCategoryRelationship> getInCategories() {
		return inCategories;
	}

	public void setInCategories(List<ContainerCategoryRelationship> inCategories) {
		this.inCategories = inCategories;
	}

}
