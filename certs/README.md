# Extra certificate authorities for container builds

Put any `.crt` files your network needs here, for example a corporate proxy's root certificate.
The build imports them into the Java and Node trust stores. Leave the folder empty if you do not need it.
Files here are git-ignored, except this README and `.gitkeep`.
