package cloud.eppo.android.dto.adapters;

import cloud.eppo.api.EppoValue;
import cloud.eppo.api.dto.BanditParametersResponse;
import cloud.eppo.api.dto.FlagConfigResponse;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.util.Date;

public class EppoModule {
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
