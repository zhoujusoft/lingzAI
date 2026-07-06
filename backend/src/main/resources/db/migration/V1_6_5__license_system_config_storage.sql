SET @current_schema = DATABASE();

INSERT INTO `system_config` (`config_key`, `config_value`, `status`)
SELECT 'service_license_bootstrap_state',
       'Om11pqp5cAnWQSDTRDAXQXTOdIslEct7Z1d2oBeaiUVvQCRTyoWsKUZJUSXJsNAg46mMR56zzCBVndaOvH3OMCvsR8BNOytp5/ltFa+Frc/faSflKzVR9O7PaGv4H93fZpnxu4G2ZqWtXj0lxjrz2Uz+X90IsUTpv4fSCQH213cfOuq4gztu2/a+k7m4aHcDuDY0ECRJbwcr4QuUqRIbxg3/1zjl+YLB8xLKIPJMZAmYvCZ02GOjohCYnnjq+AG6D7pEYiiwMdrazcfZJe22zjnMpd4BisssrETU7gDZK3fLoM1VWI3+nkdKxEp9kSANQ0GjbSHYVbD6xxjE/rtwEA==',
       1
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `system_config` WHERE `config_key` = 'service_license_bootstrap_state'
);

SET @legacy_service_license_exists = (
    SELECT COUNT(1)
    FROM information_schema.TABLES
    WHERE TABLE_SCHEMA = @current_schema
      AND TABLE_NAME = 'service_license'
);

SET @legacy_service_license_rows = 0;
SET @sql = IF(
    @legacy_service_license_exists > 0,
    'SELECT COUNT(1) INTO @legacy_service_license_rows FROM `service_license`',
    'SET @legacy_service_license_rows = 0'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE `system_config`
SET `config_value` = 'N1YmftdXgLSv+uFJKurE0F+7pWTnE3X9O0B2gegeQ4jpK/DY81SWxGEl9L5sL2smTkhucsLT+qbdyamAYUS7YWh149SFEXj/WaQwoWfm//5TBUjRc9QpfspPpy4er1Ze3jWz6Huu3zHjnheeQX1kBLZCFeJe6jciLIkIB5zCXj1k+PmjqO0q8qiyLj3mY1+yuNFS0VSPpoaD5SFBEwnTROdcumtpWfT+ePwCbsb74Qd0tUTW5KePDZ1xcf37Em4HCiWMEDxKw1ssXXjyAWM9gRXs/mWuJ42+bXxgTeQamQj8GEU1sUaMCP/35kRaIaU6tFeRMorilh4JlNI01fpTtA==',
    `status` = 1
WHERE `config_key` = 'service_license_bootstrap_state'
  AND @legacy_service_license_rows > 0;
