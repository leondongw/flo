package work.leond.flo.service;

public class HttpDataFile {

  private String filename;
  private String contentType;
  private byte[] content;

  public HttpDataFile(String filename, String contentType, byte[] content) {
    this.filename = filename;
    this.contentType = contentType;
    this.content = content;
  }

  public String filename() {
    return filename;
  }

  public String contentType() {
    return contentType;
  }

  public byte[] content() {
    return content;
  }

}
