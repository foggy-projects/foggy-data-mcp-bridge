# Frozen 9.5.0 reactor fixture

This is the ordered `<modules>` excerpt from `pom.xml` at the signed-off
candidate `03af64da7330d70d3364c9e92992710a2ed3a111`, not a copy of the current contract's expected list.
Source Git blob SHA-256: `e81a494caef2932655282ead881d06ea6c5b8cf7296f9b727fa9cd837e467fac`.

The test creates placeholder module POMs only to exercise module presence checks.
It copies the pinned v934 provisioner as LF bytes, as in a Git source archive;
its real digest is still checked against the unchanged frozen contract.
This fixture does not establish a Maven build, a release candidate identity,
or a version signoff. Production authority continues to inspect the caller's
actual root POM and rejects incompatible reactors.
