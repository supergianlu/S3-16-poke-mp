package distributed.deserializers

import java.lang.reflect.Type
import java.util.concurrent.{ConcurrentHashMap, ConcurrentMap}

import com.google.gson.{GsonBuilder, JsonDeserializationContext, JsonDeserializer, JsonElement}
import distributed.{Player, PlayerImpl}

object ConnectedUsersDeserializer extends JsonDeserializer[ConcurrentMap[Int, Player]] {
  override def deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ConcurrentMap[Int, Player] = {
    val jsonConnectedUser = json.getAsJsonObject
    val jsonKeys = jsonConnectedUser.keySet()
    val gson = new GsonBuilder().registerTypeAdapter(classOf[PlayerImpl], UserDeserializer).create()
    val map = new ConcurrentHashMap[Int, Player]()

    jsonKeys forEach(key => map.put(key.toInt, gson.fromJson(jsonConnectedUser.get(key), classOf[PlayerImpl])))

    map
  }
}