import grails.test.AbstractCliTestCase

class _InstallTests extends AbstractCliTestCase {
    protected void setUp() {
        super.setUp()
    }

    protected void tearDown() {
        super.tearDown()
    }

    void test_Install() {

        execute(["install"])

        assertEquals 0, waitForProcess()
        verifyHeader()
    }
}
