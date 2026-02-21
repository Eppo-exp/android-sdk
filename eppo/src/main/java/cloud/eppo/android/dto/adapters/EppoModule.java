package cloud.eppo.android.dto.adapters;

import cloud.eppo.api.EppoValue;
import cloud.eppo.api.dto.BanditParametersResponse;
import cloud.eppo.api.dto.FlagConfigResponse;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.util.Date;

/**
 * Jackson module providing custom deserializers for Eppo configuration types.
 *
 * <p>These deserializers are hand-rolled to avoid reliance on annotations and method names, which
 * can be unreliable when ProGuard minification is in-use.
 */
public class EppoModule {
  /**
   * Creates a Jackson module with Eppo-specific serializers and deserializers.
   *
   * @return a SimpleModule configured for Eppo types
   */
  public static SimpleModule eppoModule() {
    SimpleModule module = new SimpleModule();
    module.addDeserializer(FlagConfigResponse.class, new FlagConfigResponseDeserializer());
    module.addDeserializer(
        BanditParametersResponse.class, new BanditParametersResponseDeserializer());
    module.addDeserializer(EppoValue.class, new EppoValueDeserializer());
    module.addSerializer(EppoValue.class, new EppoValueSerializer());
    module.addSerializer(Date.class, new DateSerializer());
    return module;
  }
}
