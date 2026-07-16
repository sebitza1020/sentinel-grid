package com.defense.sentinel.service;

/** Safe, structured execution failure for an otherwise valid voice command. */
public class VoiceCommandExecutionException extends RuntimeException {

  public enum Kind {
    DRONE_NOT_FOUND,
    COMMAND_CONFLICT
  }

  private final Kind kind;
  private final String code;

  public VoiceCommandExecutionException(Kind kind, String code, String message) {
    super(message);
    this.kind = kind;
    this.code = code;
  }

  public Kind kind() {
    return kind;
  }

  public String code() {
    return code;
  }
}
