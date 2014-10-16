Nicobar


Notes
======

1. nicobar-core unit tests rely on test jars containing classes. In order for these to be
maintainable, there is a separate project nicobar-core/nicobar-test-classes. If you are modifying
the test classes, Make sure to run the 'copyTestClassJars' task on 'nicobar-test-classes' in order to regenerate
the contents of nicobar-core/src/test/resources/jars. 
