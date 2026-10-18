package com.openmarket.member.command.application;

import java.util.UUID;

public record RemoveAddressCommand(UUID memberId, UUID addressId) {}
