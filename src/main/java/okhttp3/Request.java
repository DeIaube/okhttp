/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3;

import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import okhttp3.internal.Util;
import okhttp3.internal.http.HttpMethod;

/**
 * 一个HTTP请求的抽象.
 * 这个类的实例是不可变的如果{@link #body}为null或者自身为不可变
 */
public final class Request {
  final HttpUrl url;
  // post get
  final String method;
  final Headers headers;
  // 封装Request参数 可能为null
  final @Nullable RequestBody body;
  final Map<Class<?>, Object> tags;

  private volatile @Nullable CacheControl cacheControl; // 懒加载

  Request(Builder builder) {
    this.url = builder.url;
    this.method = builder.method;
    this.headers = builder.headers.build();
    this.body = builder.body;
    this.tags = Util.immutableMap(builder.tags);
  }

  public HttpUrl url() {
    return url;
  }

  public String method() {
    return method;
  }

  public Headers headers() {
    return headers;
  }

  public @Nullable String header(String name) {
    return headers.get(name);
  }

  public List<String> headers(String name) {
    return headers.values(name);
  }

  public @Nullable RequestBody body() {
    return body;
  }

  /**
   * 返回一个关联{@code Object.class}作为key的tag,如果没有tag作为key则返回null.
   *
   * this method never returned null if no tag was attached. Instead it
   * returned either this request, or the request upon which this request was derived with {@link
   * #newBuilder()}.
   */
  public @Nullable Object tag() {
    return tag(Object.class);
  }

  /**
   * 返回一个tag作为{@code type}的key,如果没有tag则返回null
   */
  public @Nullable <T> T tag(Class<? extends T> type) {
    return type.cast(tags.get(type));
  }

  public Builder newBuilder() {
    return new Builder(this);
  }

  /**
   * 返回这个的response的cache control,即便没有cache control也不会返回null
   */
  public CacheControl cacheControl() {
    CacheControl result = cacheControl;
    return result != null ? result : (cacheControl = CacheControl.parse(headers));
  }

    /**
     * 通过scheme判断http与https
     */
  public boolean isHttps() {
    return url.isHttps();
  }

  @Override public String toString() {
    return "Request{method="
        + method
        + ", url="
        + url
        + ", tags="
        + tags
        + '}';
  }

  public static class Builder {
    @Nullable HttpUrl url;
    String method;
    Headers.Builder headers;
    @Nullable RequestBody body;

    /** 一个tag的可变map, 如果没有tag则是一个不可变空map*/
    Map<Class<?>, Object> tags = Collections.emptyMap();

    /** 默认Method是GET */
    public Builder() {
      this.method = "GET";
      this.headers = new Headers.Builder();
    }

    Builder(Request request) {
      this.url = request.url;
      this.method = request.method;
      this.body = request.body;
      this.tags = request.tags.isEmpty()
          ? Collections.<Class<?>, Object>emptyMap()
          : new LinkedHashMap<>(request.tags);
      this.headers = request.headers.newBuilder();
    }

    public Builder url(HttpUrl url) {
      if (url == null) throw new NullPointerException("url == null");
      this.url = url;
      return this;
    }

    /**
     * 设置请求的目标url
     *
     * @throws IllegalArgumentException 如果 {@code url}不是一个有效的http、https url.
     * 避免调用{@link HttpUrl#parse}抛出异常;如果传入一个无效的资源定位符会返回null.
     */
    public Builder url(String url) {
      if (url == null) throw new NullPointerException("url == null");

      // Silently replace web socket URLs with HTTP URLs.
      if (url.regionMatches(true, 0, "ws:", 0, 3)) {
        url = "http:" + url.substring(3);
      } else if (url.regionMatches(true, 0, "wss:", 0, 4)) {
        url = "https:" + url.substring(4);
      }

      return url(HttpUrl.get(url));
    }

    /**
     * 设置请求的目标url
     *
     * @throws IllegalArgumentException 当url不是http或者https时抛出异常
     */
    public Builder url(URL url) {
      if (url == null) throw new NullPointerException("url == null");
      return url(HttpUrl.get(url.toString()));
    }

    /**
     * 为header的value设置name,如果同名将会被覆盖
     */
    public Builder header(String name, String value) {
      headers.set(name, value);
      return this;
    }

    /**
     * Adds a header with {@code name} and {@code value}. Prefer this method for multiply-valued
     * headers like "Cookie".
     *
     * <p>Note that for some headers including {@code Content-Length} and {@code Content-Encoding},
     * OkHttp may replace {@code value} with a header derived from the request body.
     */
    public Builder addHeader(String name, String value) {
      headers.add(name, value);
      return this;
    }

    /** 移除所有名为name的header */
    public Builder removeHeader(String name) {
      headers.removeAll(name);
      return this;
    }

    /** 移除所有header */
    public Builder headers(Headers headers) {
      this.headers = headers.newBuilder();
      return this;
    }

    /**
     * 设置缓存控制头 {@code Cache-Control}, 并替换已存的缓存控制头
     * 如果缓存控制头没有定义任何指令,则会清除此请求的缓存控制头
     */
    public Builder cacheControl(CacheControl cacheControl) {
      String value = cacheControl.toString();
      if (value.isEmpty()) return removeHeader("Cache-Control");
      return header("Cache-Control", value);
    }

    public Builder get() {
      return method("GET", null);
    }

    public Builder head() {
      return method("HEAD", null);
    }

    public Builder post(RequestBody body) {
      return method("POST", body);
    }

    public Builder delete(@Nullable RequestBody body) {
      return method("DELETE", body);
    }

    public Builder delete() {
      return delete(Util.EMPTY_REQUEST);
    }

    public Builder put(RequestBody body) {
      return method("PUT", body);
    }

    public Builder patch(RequestBody body) {
      return method("PATCH", body);
    }

    public Builder method(String method, @Nullable RequestBody body) {
      if (method == null) throw new NullPointerException("method == null");
      if (method.length() == 0) throw new IllegalArgumentException("method.length() == 0");
      if (body != null && !HttpMethod.permitsRequestBody(method)) {
        throw new IllegalArgumentException("method " + method + " must not have a request body.");
      }
      if (body == null && HttpMethod.requiresRequestBody(method)) {
        throw new IllegalArgumentException("method " + method + " must have a request body.");
      }
      this.method = method;
      this.body = body;
      return this;
    }

    /** Attaches {@code tag} to the request using {@code Object.class} as a key. */
    public Builder tag(@Nullable Object tag) {
      return tag(Object.class, tag);
    }

    /**
     * Attaches {@code tag} to the request using {@code type} as a key. Tags can be read from a
     * request using {@link Request#tag}. Use null to remove any existing tag assigned for {@code
     * type}.
     *
     * <p>Use this API to attach timing, debugging, or other application data to a request so that
     * you may read it in interceptors, event listeners, or callbacks.
     */
    public <T> Builder tag(Class<? super T> type, @Nullable T tag) {
      if (type == null) throw new NullPointerException("type == null");

      if (tag == null) {
        tags.remove(type);
      } else {
        if (tags.isEmpty()) tags = new LinkedHashMap<>();
        tags.put(type, type.cast(tag));
      }

      return this;
    }

    public Request build() {
      if (url == null) throw new IllegalStateException("url == null");
      return new Request(this);
    }
  }
}
