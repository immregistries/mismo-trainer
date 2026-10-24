CREATE TABLE configuration
(
    -- Field this table needs to support
    --  String configurationId = "";
    --  String worldName = "";
    --  String islandName = "";
    --  int generation = 0;
    --  double generationScore = 0.0;
    --  Date generatedDate = null;
    --  String hashForSignature;
    --  String configurationScript = "";

    configuration_id int NOT NULL PRIMARY KEY AUTO_INCREMENT,
    world_name varchar(250) NOT NULL,
    island_name varchar(250) NOT NULL,
    generation int NOT NULL,
    generation_score double NOT NULL,
    generated_date datetime NOT NULL,
    hash_for_signature varchar(250) NOT NULL,
    configuration_script text NOT NULL
);