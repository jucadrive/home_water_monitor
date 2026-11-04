package com.juca.homewatermonitorbackend.session.application.control;

import java.util.UUID;

/** Compare under the device row lock so a manual action cannot be replaced after preflight. */
public record RecoveryExpectation(UUID activeCommand,UUID boot,UUID execution,int round,UUID cultivation) { }
