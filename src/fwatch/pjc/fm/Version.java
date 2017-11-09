package fwatch.pjc.fm;

/**
 * @author Friedhold Matz, October 2017.
 * 
 */
class Version
{ 
  // definig of PJC_VERSION_STRING ::    major.minor.revision.build
  private static final String PJC_VERSION_STRING  = "00.02.01.171109";
  private static final int    PJC_VERSION_INTEGER =       201171109;
    
  private Version() {  
  }
  
  public static String getPJCVersion() {
    return PJC_VERSION_STRING;
  }
  
  public static Integer getPJCVersionInt() {
    return PJC_VERSION_INTEGER;
  }
  
  public static String getOS() {
        return System.getProperty("os.name");
  }
            
  public static String getOSVersion() {
        return System.getProperty("os.version");
  }
    
  public static String getJavaOS() {
      if (System.getProperty("os.arch").contains("64")) {
          return "64 Bit";
      } else if (System.getProperty("os.arch").contains("86")) {
          return "32 Bit";
      } else {
          return "??? Java OS property not found.";
      }
  }
  
  public static String getJavaVersion() {
      return System.getProperty("java.version");
  }
  
  public static String getJavaTempDir() {
      return System.getProperty("java.io.tmpdir");
  }
  
}

