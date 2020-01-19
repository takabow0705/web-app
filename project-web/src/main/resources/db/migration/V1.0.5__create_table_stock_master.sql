CREATE TABLE stock_master_jp (
    STOCK_CODE CHAR(50) NOT NULL,
    STOCK_NAME VARCHAR(500) NOT NULL,
    UNIT DECIMAL(10 , 0 ) NOT NULL,
    MARKET_DIVISION_CODE VARCHAR(50) NOT NULL  COMMENT '10:市場第一部,20:市場第二部,30:JASDAQ,40:マザーズ,90:その他',
    MARKET VARCHAR(100) ,
    33SECTOR_CODE CHAR(5),
    33SECTOR_DETAIL VARCHAR(50),
    17SECTOR_CODE CHAR(5),
    17SECTOR_DETAIL VARCHAR(50),
    PRIMARY KEY (STOCK_CODE)
)