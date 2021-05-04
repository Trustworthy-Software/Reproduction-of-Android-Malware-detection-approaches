package revealdroid.features.addresses;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Assert;

/**
 * Created by joshua on 4/20/17.
 */
public class TestNetAddressRegExes {

    @Test
    public void testIpRegEx() {
        Matcher m = ExtractNetAddresses.ipAddrPattern.matcher("192.168.0.1");
        Assert.assertEquals("192.168.0.1",ExtractNetAddresses.whileFindPrintGroup(m));

        m = ExtractNetAddresses.ipAddrPattern.matcher("a 192.168.0.1 b");
        Assert.assertEquals("192.168.0.1",ExtractNetAddresses.whileFindPrintGroup(m));

        m = ExtractNetAddresses.ipAddrPattern.matcher("hey everybody");
        Assert.assertNull(ExtractNetAddresses.whileFindPrintGroup(m));
    }

    @Test
    public void testHostNameRegEx() {
        Matcher m = ExtractNetAddresses.hostnamePattern.matcher("www.google.com");
        Assert.assertEquals("www.google.com",ExtractNetAddresses.ifMatchesPrintGroup(m));

        m = ExtractNetAddresses.hostnamePattern.matcher("google.com");
        Assert.assertEquals("google.com",ExtractNetAddresses.ifMatchesPrintGroup(m));

        m = ExtractNetAddresses.hostnamePattern.matcher("hey everybody");
        Assert.assertNull(ExtractNetAddresses.ifMatchesPrintGroup(m));

        m = ExtractNetAddresses.hostnamePattern.matcher("true");
        Assert.assertNull(ExtractNetAddresses.ifMatchesPrintGroup(m));
    }

    @Test
    public void testIriRegEx() {
        Matcher m = ExtractNetAddresses.iriPattern.matcher("http://www.google.com/a/b/v.htm");
        Assert.assertEquals("http://www.google.com/a/b/v.htm",ExtractNetAddresses.ifMatchesPrintGroup(m));

        m = ExtractNetAddresses.iriPattern.matcher("www.google.com");
        Assert.assertNull(ExtractNetAddresses.ifMatchesPrintGroup(m));

    }

}
