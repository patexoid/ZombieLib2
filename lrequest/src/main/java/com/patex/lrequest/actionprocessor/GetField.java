package com.patex.lrequest.actionprocessor;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.patex.lrequest.ActionHandler;
import com.patex.lrequest.ActionResult;
import com.patex.lrequest.FlowType;
import com.patex.lrequest.Value;
import com.patex.lrequest.WrongActionSyntaxException;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.stereotype.Service;

@SuppressWarnings("unchecked")
@Service
public class GetField implements ActionHandler {

  private final LambdaInfoStorage lambdaStorage = new LambdaInfoStorage();

  @Override
  public ActionResult createFuncton(FlowType input, Value... values)
      throws WrongActionSyntaxException {
    Class inputType = input.getReturnType();
    if (input.is(FlowType.Type.initial) ||
        values.length != 1 ||
        !String.class.isAssignableFrom(values[0].getResultClass())) {
      throw new WrongActionSyntaxException("Object.GetField(\"FieldName\") or Stream.GetField(\"FieldName\")");
    }
    String fieldName = (String) values[0].getResultSupplier().get();
    LambdaInfo lambda = lambdaStorage.getLambda(inputType, fieldName);

    if (input.is((FlowType.Type.stream))) {
      return new ActionResult(s -> {
        Stream inputS = (Stream) s;
        if (lambda.isCollection()) {
          Function<Object, Collection> result = lambda.getFunction();
          return inputS.flatMap(result.andThen(Collection::stream));
        } else {
          return inputS.map(lambda.getFunction());
        }
      }, FlowType.streamResult(lambda.getReturnType()));
    } else {
      if (lambda.isCollection()) {
        Function<Object, Collection> result = lambda.getFunction();
        return new ActionResult(result.andThen(Collection::stream), FlowType.streamResult(lambda.getReturnType()));
      } else {
        return new ActionResult(lambda.getFunction(), FlowType.objResult(lambda.getReturnType()));
      }
    }
  }

  private static class LambdaInfoStorage {

    private final Lookup lookup = MethodHandles.lookup();
    LoadingCache<LambdaKey, LambdaInfo> lambdaCache = CacheBuilder.newBuilder().weakKeys().build(
        new CacheLoader<>() {
          @Override
          public LambdaInfo load(LambdaKey key) {
            return getLambda(key);
          }
        });

    @SneakyThrows
    public LambdaInfo getLambda(Class type, String field) {
      return lambdaCache.get(new LambdaKey(type, field));
    }


    @SneakyThrows
    private LambdaInfo getLambda(LambdaKey key) {
      Method reflected = getMethod(key);

      MethodHandle methodHandle = lookup.unreflect(reflected);

      MethodType func = methodHandle.type();
      CallSite site = LambdaMetafactory.metafactory(lookup,
          "apply",
          MethodType.methodType(Function.class),
          func.generic(), methodHandle, func);

      MethodHandle factory = site.getTarget();
      Function function = (Function) factory.invoke();

      Class returnType = reflected.getReturnType();
      if (Collection.class.isAssignableFrom(returnType)) {
        Type type = reflected.getGenericReturnType();
        if (type instanceof ParameterizedType) {
          Type elementType = ((ParameterizedType) type).getActualTypeArguments()[0];
          return new LambdaInfo(function, Class.forName(elementType.getTypeName()), true);
        }
      }
      return new LambdaInfo(function, returnType, false);
    }

    private Method getMethod(LambdaKey key) throws NoSuchMethodException {
      String field = key.getField();
      Class type = key.getType();
      String getterName = "get" + field.substring(0, 1).toUpperCase() + field.substring(1);
      return type.getMethod(getterName);
    }
  }

  @RequiredArgsConstructor
  @Getter
  private static class LambdaInfo {

    private final Function function;
    private final Class returnType;
    private final boolean collection;
  }

  @Data
  private static class LambdaKey {

    private final Class type;
    private final String field;
  }
}
