# Wikipedia Categories Graph
This repository contains the code to create the graph of the categories in Wikipedia.

## Table of Contents
- [Introduction](#introduction)
- [Installation](#installation)
- [Usage](#usage)

## Introduction

Wikipedia has around 1.7 million categories.
These categories are connected to each others in a graph structure. As part of the project of finding the most common words in the [Main Topic Classification Categories](https://en.wikipedia.org/wiki/Category:Main_topic_classifications), it was needed to map articles to one of these categories. We needed to create the _Categories Graph_. We used it to calculate the shortest path for mapping the articles to the closest macro category. More on this project [here](https://github.com/cenh/Wikipedia-Heavy-Hitters/).

This repository contains only the code to create the graph, with some minor fixes to create the complete graph and not a partial one as in the project linked.

## Installation

Make sure maven is installed on the host


The java program root folder is [this one](./wikipedia-graph-neo4j/).

Open the terminal in this folder and run the following command
    
    mvn package

Install Neo4j server and make sure it is running the bolt protocol on port 7687.

## Usage

### Creating the category database

Download the sql dumps of these wikipedia tables [from the official page](https://dumps.wikimedia.org/enwiki/): category, categorylinks, page.

Change directory into the target directory (./wikipedia-graph-neo4j/target) and run:

    java -jar .\wikipedia-graph-neo4j-0.0.1-SNAPSHOT.jar 
    --spring.profiles.active=create-wiki-graph-db 
    --category-dump-file=<path to category file> 
    --page-dump-file=<path to page file>
    --category-links-dump-file=<path to categorylinks file>
    --base-folder=<the folder where the program outputs files>

Make sure to replace the placeholder <> with the paths to the downloaded files.
This process takes several hours.

### Exposing the HTTP interface

Simply run

    java -jar .\wikipedia-graph-neo4j-0.0.1-SNAPSHOT.jar

By default it will be listening on localhost:8080, exposing some APIs.

Example of article mapping http request:

    http://localhost:8080/mapCategory?startCategories=Database_management_systems::Databases&endCategories=Arts::Geography::Technology::Science::People::World

The query params start-category represents the categories the articles has, the end-categories represent the macro-category. One of the macro-category will be returned as result of the mapping. More on this algorithm of mapping can be read in the final paper of the [project](https://github.com/cenh/Wikipedia-Heavy-Hitters/).

Example of shortest path http request:

    http://localhost:8080/shortestPath?startCategory=Database_management_systems&endCategory=Arts&maxPathLength=10

You may have implemented other APIs to query the graph. If you used _@Controller Spring Annotation_, these will be available at the same host and port.

## License
The package is Open Source Software released under the [MIT](LICENSE) license.
