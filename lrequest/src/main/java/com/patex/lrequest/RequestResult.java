package com.patex.lrequest;

import java.util.function.Supplier;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class RequestResult<R> {

  private final Class<R> resultClass;
  private final Supplier<R> resultSupplier;

}