// Neo4j Indexes
// Execute these in Neo4j Browser after first startup

// Node property indexes
CREATE INDEX method_name_idx IF NOT EXISTS FOR (m:Method) ON (m.name);
CREATE INDEX method_file_idx IF NOT EXISTS FOR (m:Method) ON (m.filePath);
CREATE INDEX class_name_idx IF NOT EXISTS FOR (c:Class) ON (c.name);
CREATE INDEX class_fullname_idx IF NOT EXISTS FOR (c:Class) ON (c.fullName);
CREATE INDEX service_name_idx IF NOT EXISTS FOR (s:Service) ON (s.name);
CREATE INDEX package_path_idx IF NOT EXISTS FOR (p:Package) ON (p.path);

// Traceability indexes
CREATE INDEX commit_hash_idx IF NOT EXISTS FOR (c:Commit) ON (c.hash);
CREATE INDEX author_email_idx IF NOT EXISTS FOR (a:Author) ON (a.email);
CREATE INDEX author_name_idx IF NOT EXISTS FOR (a:Author) ON (a.name);

// Business semantic indexes
CREATE INDEX domain_name_idx IF NOT EXISTS FOR (d:Domain) ON (d.name);
CREATE INDEX flow_name_idx IF NOT EXISTS FOR (f:BusinessFlow) ON (f.name);
CREATE INDEX entity_name_idx IF NOT EXISTS FOR (e:Entity) ON (e.name);

// Project indexes
CREATE INDEX project_name_idx IF NOT EXISTS FOR (p:Project) ON (p.name);

// Compound indexes
CREATE INDEX method_name_file_idx IF NOT EXISTS FOR (m:Method) ON (m.name, m.filePath);
CREATE INDEX commit_time_idx IF NOT EXISTS FOR (c:Commit) ON (c.authoredAt);