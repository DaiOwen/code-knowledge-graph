// Neo4j Constraints
// Execute these in Neo4j Browser after first startup

// Unique constraints for nodes
CREATE CONSTRAINT method_unique IF NOT EXISTS
FOR (m:Method)
REQUIRE (m.projectId, m.filePath, m.name, m.startLine) IS UNIQUE;

CREATE CONSTRAINT class_unique IF NOT EXISTS
FOR (c:Class)
REQUIRE (c.projectId, c.fullName) IS UNIQUE;

CREATE CONSTRAINT commit_unique IF NOT EXISTS
FOR (c:Commit)
REQUIRE (c.projectId, c.hash) IS UNIQUE;

CREATE CONSTRAINT author_unique IF NOT EXISTS
FOR (a:Author)
REQUIRE (a.email) IS UNIQUE;

CREATE CONSTRAINT project_unique IF NOT EXISTS
FOR (p:Project)
REQUIRE (p.id) IS UNIQUE;