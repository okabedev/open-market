package com.openmarket.member.command.application;

import java.util.UUID;

public record SetDefaultAddressCommand(UUID memberId, UUID addressId) {}
