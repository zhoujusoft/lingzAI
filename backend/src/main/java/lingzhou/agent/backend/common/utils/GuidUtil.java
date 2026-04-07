package lingzhou.agent.backend.common.utils;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GuidUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(GuidUtil.class);

    public static String ToShort() {
        UUID uuid = UUID.randomUUID();

        String id = uuid.toString().toLowerCase();
        id = id.replace("-", "");

        return id.substring(0, 16);
    }

    public static boolean IsValidGuid(String value) {
        if (StringUtils.isEmpty(value)) return false;

        String regex = "^[{]?[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}[}]?$";

        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(value);

        return m.matches();
    }
}
