package com.defense.sentinel.service;

/** Distinguishes an unavailable AI uplink from a model response that is unsafe to execute. */
public class VoiceCommandParsingException extends RuntimeException {

  public enum Kind {
    INVALID_COMMAND,
    UPLINK_FAILURE
  }

  private final Kind kind;

  public VoiceCommandParsingException(Kind kind, String message) {
    super(message);
    this.kind = kind;
  }

  public VoiceCommandParsingException(Kind kind, String message, Throwable cause) {
    super(message, cause);
    this.kind = kind;
  }

  public Kind kind() {
    return kind;
  }
}
