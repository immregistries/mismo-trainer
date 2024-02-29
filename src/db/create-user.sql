CREATE USER 'mv_web'@'localhost' IDENTIFIED WITH mysql_native_password BY 'cArn88r0w';

GRANT ALL PRIVILEGES ON matching_validation.* TO 'mv_web'@'localhost' WITH GRANT OPTION;