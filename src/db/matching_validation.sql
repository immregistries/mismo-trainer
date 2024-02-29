-- MySQL dump 10.13  Distrib 8.0.27, for Win64 (x86_64)
--
-- Host: localhost    Database: matching_validation
-- ------------------------------------------------------
-- Server version	8.0.27

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `match_item`
--

DROP TABLE IF EXISTS `match_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `match_item` (
  `match_item_id` int NOT NULL AUTO_INCREMENT,
  `match_set_id` int NOT NULL,
  `label` varchar(250) NOT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `patient_data_a` text NOT NULL,
  `patient_data_b` text NOT NULL,
  `expect_status` varchar(20) NOT NULL,
  `user_id` int NOT NULL,
  `update_date` datetime NOT NULL,
  `data_source` varchar(120) NOT NULL,
  PRIMARY KEY (`match_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `match_item`
--

LOCK TABLES `match_item` WRITE;
/*!40000 ALTER TABLE `match_item` DISABLE KEYS */;
/*!40000 ALTER TABLE `match_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `match_set`
--

DROP TABLE IF EXISTS `match_set`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `match_set` (
  `match_set_id` int NOT NULL AUTO_INCREMENT,
  `label` varchar(250) NOT NULL,
  `update_date` datetime NOT NULL,
  PRIMARY KEY (`match_set_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `match_set`
--

LOCK TABLES `match_set` WRITE;
/*!40000 ALTER TABLE `match_set` DISABLE KEYS */;
INSERT INTO `match_set` VALUES (1,'Test 2023','2023-03-10 06:42:01');
/*!40000 ALTER TABLE `match_set` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `patient`
--

DROP TABLE IF EXISTS `patient`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `patient` (
  `patient_id` int NOT NULL,
  `name_first` varchar(100) DEFAULT NULL,
  `name_last` varchar(100) DEFAULT NULL,
  `name_middle` varchar(100) DEFAULT NULL,
  `name_suffix` varchar(100) DEFAULT NULL,
  `birth_date` varchar(100) DEFAULT NULL,
  `grd_name_first` varchar(100) DEFAULT NULL,
  `grd_name_last` varchar(100) DEFAULT NULL,
  `mth_name_first` varchar(100) DEFAULT NULL,
  `mth_name_middle` varchar(100) DEFAULT NULL,
  `mth_name_maiden` varchar(100) DEFAULT NULL,
  `phone` varchar(100) DEFAULT NULL,
  `race` varchar(100) DEFAULT NULL,
  `ethnicity` varchar(100) DEFAULT NULL,
  `adr_street1` varchar(100) DEFAULT NULL,
  `adr_street2` varchar(100) DEFAULT NULL,
  `adr_city` varchar(100) DEFAULT NULL,
  `adr_state` varchar(100) DEFAULT NULL,
  `adr_zip` varchar(100) DEFAULT NULL,
  `gender` varchar(100) DEFAULT NULL,
  `birth_type` varchar(100) DEFAULT NULL,
  `birth_order` varchar(100) DEFAULT NULL,
  `mrns` varchar(100) DEFAULT NULL,
  `ssn` varchar(100) DEFAULT NULL,
  `medicaid` varchar(100) DEFAULT NULL,
  `shot_history` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`patient_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `patient`
--

LOCK TABLES `patient` WRITE;
/*!40000 ALTER TABLE `patient` DISABLE KEYS */;
/*!40000 ALTER TABLE `patient` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `user_id` int NOT NULL AUTO_INCREMENT,
  `name` varchar(30) DEFAULT NULL,
  `email` varchar(120) DEFAULT NULL,
  `password` varchar(30) DEFAULT NULL,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user`
--

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (1,'Nathan Bunker','nbunker@immregistries.org','welcome');
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2024-02-03  6:46:31
