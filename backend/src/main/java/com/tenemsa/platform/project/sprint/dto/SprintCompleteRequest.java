package com.tenemsa.platform.project.sprint.dto;

/**
 * Where unfinished issues go when a sprint is completed: a PLANNED sprint of the same
 * project, or the backlog when moveUnfinishedTo is null.
 */
public record SprintCompleteRequest(Long moveUnfinishedTo) {}
