// Minimal hand-rolled JSON serializer/deserializer for rage-native's structs.
//
// LIMITATIONS (intentional — no third-party deps):
//   - The parser accepts standard JSON (objects, arrays, strings, numbers,
//     true/false/null) but does NOT support comments or trailing commas.
//   - Numbers are parsed as `double`; integers >2^53 may lose precision.
//   - The parser is recursive; deeply nested inputs (depth > 100) may overflow
//     the stack. Inputs from Kotlin are shallow (max ~3 levels), so this is
//     acceptable.
//   - String escaping handles \", \\, \/, \b, \f, \n, \r, \t and \uXXXX.
//   - The serializer outputs canonical compact JSON (no whitespace between
//     tokens) — kotlinx.serialization accepts this on the Kotlin side.
//
// All serialization functions return a std::string. Deserialization functions
// return the parsed struct (or a default-constructed struct on parse failure,
// with the parse error logged via __android_log).
#pragma once

#include "core/RageTypes.h"

#include <optional>
#include <string>
#include <utility>
#include <vector>

namespace rage::native {

// ============================================================
// Low-level JSON value model
// ============================================================

struct JsonValue {
    enum class Type { Null, Bool, Number, String, Array, Object };

    Type                                                       type = Type::Null;
    bool                                                       boolValue = false;
    double                                                     numberValue = 0.0;
    std::string                                                stringValue;
    std::vector<JsonValue>                                     arrayValue;
    std::vector<std::pair<std::string, JsonValue>>             objectValue;

    static JsonValue makeBool(bool v);
    static JsonValue makeNumber(double v);
    static JsonValue makeString(std::string v);
    static JsonValue makeArray(std::vector<JsonValue> v);
    static JsonValue makeObject(std::vector<std::pair<std::string, JsonValue>> v);
    static JsonValue makeNull();

    const JsonValue* find(const std::string& key) const;
    std::optional<bool>        asBool(const std::string& key) const;
    std::optional<double>      asNumber(const std::string& key) const;
    std::optional<int64_t>     asInt(const std::string& key) const;
    std::optional<std::string> asString(const std::string& key) const;
    const std::vector<JsonValue>* asArray(const std::string& key) const;
    const std::vector<std::pair<std::string, JsonValue>>* asObject() const;
};

// Parse a JSON string. Returns nullopt on failure (and logs via __android_log).
std::optional<JsonValue> parseJson(const std::string& json);

// Serialize a JsonValue to canonical compact JSON.
std::string serializeJson(const JsonValue& v);

// Escape a string for embedding in JSON (adds surrounding quotes).
std::string jsonString(const std::string& s);

// Escape a string content only (no surrounding quotes).
std::string jsonEscape(const std::string& s);

// ============================================================
// rage-native struct serializers
// ============================================================

std::string serializeTask(const NativeTask& t);
std::string serializeConfig(const NativeRageConfig& c);
std::string serializeResult(const NativeExecutionResult& r);
std::string serializeMetrics(const NativeMetrics& m);
std::string serializeEvent(const NativeEvent& e);
std::string serializeSubtask(const NativeSubtask& s);

// ============================================================
// rage-native struct deserializers
// ============================================================

NativeTask            deserializeTask(const std::string& json);
NativeRageConfig      deserializeConfig(const std::string& json);
NativeExecutionResult deserializeResult(const std::string& json);
NativeMetrics         deserializeMetrics(const std::string& json);
NativeEvent           deserializeEvent(const std::string& json);
NativeSubtask         deserializeSubtask(const std::string& json);

} // namespace rage::native
