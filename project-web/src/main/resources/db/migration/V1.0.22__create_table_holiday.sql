CREATE TABLE IF NOT EXISTS  `holiday` (
    `ID` BIGINT NOT NULL AUTO_INCREMENT,
    `DATE`   DATE NOT NULL,
    `DETAIL` VARCHAR(500) NOT NULL,
    `COUNTRY_CODE` CHAR(5) NOT NULL,
    PRIMARY KEY (ID,COUNTRY_CODE,DATE)
);