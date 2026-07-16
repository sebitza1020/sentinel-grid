package com.defense.sentinel.service;

/** Raised when no collision-free route can be produced within the planner's safety limits. */
public class RouteBlockedException extends RuntimeException {

  public RouteBlockedException(String message) {
    super(message);
  }
}
