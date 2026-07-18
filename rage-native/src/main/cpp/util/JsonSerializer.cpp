#include "util/JsonSerializer.h"

#include <android/log.h>
#include <cctype>
#include <cmath>
#include <cstdint>
#include <cstring>
#include <sstream>

namespace rage::native {

// ---- JsonValue factory helpers ----
JsonValue JsonValue::makeBool(bool v) {
    JsonValue x; x.type = Type::Bool; x.boolValue = v; return x;
}
JsonValue JsonValue::makeNumber(double v) {
    JsonValue x; x.type = Type::Number; x.numberValue = v; return x;
}
JsonValue JsonValue::makeString(std::string v) {
    JsonValue x; x.type = Type::String; x.stringValue = std::move(v); return x;
}
JsonValue JsonValue::makeArray(std::vector<JsonValue> v) {
    JsonValue x; x.type = Type::Array; x.arrayValue = std::move(v); return x;
}
JsonValue JsonValue::makeObject(std::vector<std::pair<std::string, JsonValue>> v) {
    JsonValue x; x.type = Type::Object; x.objectValue = std::move(v); return x;
}
JsonValue JsonValue::makeNull() {
    JsonValue x; x.type = Type::Null; return x;
}

const JsonValue* JsonValue::find(const std::string& key) const {
    for (const auto& [k, v] : objectValue) {
        if (k == key) return &v;
    }
    return nullptr;
}
std::optional<bool> JsonValue::asBool(const std::string& key) const {
    const JsonValue* v = find(key);
    if (!v || v->type != Type::Bool) return std::nullopt;
    return v->boolValue;
}
std::optional<double> JsonValue::asNumber(const std::string& key) const {
    const JsonValue* v = find(key);
    if (!v || v->type != Type::Number) return std::nullopt;
    return v->numberValue;
}
std::optional<int64_t> JsonValue::asInt(const std::string& key) const {
    auto d = asNumber(key);
    if (!d) return std::nullopt;
    return static_cast<int64_t>(*d);
}
std::optional<std::string> JsonValue::asString(const std::string& key) const {
    const JsonValue* v = find(key);
    if (!v || v->type != Type::String) return std::nullopt;
    return v->stringValue;
}
const std::vector<JsonValue>* JsonValue::asArray(const std::string& key) const {
    const JsonValue* v = find(key);
    if (!v || v->type != Type::Array) return nullptr;
    return &v->arrayValue;
}
const std::vector<std::pair<std::string, JsonValue>>* JsonValue::asObject() const {
    if (type != Type::Object) return nullptr;
    return &objectValue;
}

// ============================================================
// Escaping

namespace {
constexpr const char* TAG = "RageJson";
} // anonymous namespace


// ============================================================

std::string jsonEscape(const std::string& s) {
    std::string out;
    out.reserve(s.size() + 2);
    for (char c : s) {
        switch (c) {
            case '"':  out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\b': out += "\\b";  break;
            case '\f': out += "\\f";  break;
            case '\n': out += "\\n";  break;
            case '\r': out += "\\r";  break;
            case '\t': out += "\\t";  break;
            default:
                if (static_cast<unsigned char>(c) < 0x20) {
                    char buf[8];
                    std::snprintf(buf, sizeof(buf), "\\u%04x", static_cast<int>(c));
                    out += buf;
                } else {
                    out.push_back(c);
                }
        }
    }
    return out;
}

std::string jsonString(const std::string& s) {
    return "\"" + jsonEscape(s) + "\"";
}

// ============================================================
// Parser (recursive descent)
namespace {
// ============================================================

class Parser {
public:
    explicit Parser(const std::string& s) : s_(s), i_(0) {}

    std::optional<JsonValue> parse() {
        skipWs();
        if (i_ >= s_.size()) return std::nullopt;
        std::optional<JsonValue> v = parseValue();
        if (!v) return std::nullopt;
        skipWs();
        if (i_ != s_.size()) {
            __android_log_print(ANDROID_LOG_WARN, TAG,
                                "trailing chars at pos %zu", i_);
            return std::nullopt;
        }
        return v;
    }

private:
    const std::string& s_;
    size_t             i_;

    void skipWs() {
        while (i_ < s_.size()) {
            char c = s_[i_];
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') ++i_;
            else break;
        }
    }

    bool match(char c) {
        if (i_ < s_.size() && s_[i_] == c) { ++i_; return true; }
        return false;
    }

    std::optional<JsonValue> parseValue() {
        skipWs();
        if (i_ >= s_.size()) return std::nullopt;
        char c = s_[i_];
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '"') return parseString();
        if (c == 't' || c == 'f') return parseBool();
        if (c == 'n') return parseNull();
        if (c == '-' || (c >= '0' && c <= '9')) return parseNumber();
        __android_log_print(ANDROID_LOG_WARN, TAG,
                            "unexpected char '%c' at pos %zu", c, i_);
        return std::nullopt;
    }

    std::optional<JsonValue> parseObject() {
        ++i_; // consume '{'
        std::vector<std::pair<std::string, JsonValue>> members;
        skipWs();
        if (match('}')) return JsonValue::makeObject(std::move(members));
        for (;;) {
            skipWs();
            if (i_ >= s_.size() || s_[i_] != '"') return std::nullopt;
            auto key = parseString();
            if (!key) return std::nullopt;
            skipWs();
            if (!match(':')) return std::nullopt;
            auto val = parseValue();
            if (!val) return std::nullopt;
            members.emplace_back(key->stringValue, std::move(*val));
            skipWs();
            if (match(',')) continue;
            if (match('}')) return JsonValue::makeObject(std::move(members));
            return std::nullopt;
        }
    }

    std::optional<JsonValue> parseArray() {
        ++i_; // consume '['
        std::vector<JsonValue> items;
        skipWs();
        if (match(']')) return JsonValue::makeArray(std::move(items));
        for (;;) {
            auto v = parseValue();
            if (!v) return std::nullopt;
            items.push_back(std::move(*v));
            skipWs();
            if (match(',')) continue;
            if (match(']')) return JsonValue::makeArray(std::move(items));
            return std::nullopt;
        }
    }

    std::optional<JsonValue> parseString() {
        ++i_; // consume opening quote
        std::string out;
        while (i_ < s_.size()) {
            char c = s_[i_++];
            if (c == '"') return JsonValue::makeString(std::move(out));
            if (c == '\\') {
                if (i_ >= s_.size()) return std::nullopt;
                char e = s_[i_++];
                switch (e) {
                    case '"':  out.push_back('"');  break;
                    case '\\': out.push_back('\\'); break;
                    case '/':  out.push_back('/');  break;
                    case 'b':  out.push_back('\b'); break;
                    case 'f':  out.push_back('\f'); break;
                    case 'n':  out.push_back('\n'); break;
                    case 'r':  out.push_back('\r'); break;
                    case 't':  out.push_back('\t'); break;
                    case 'u': {
                        if (i_ + 4 > s_.size()) return std::nullopt;
                        unsigned int cp = 0;
                        for (int k = 0; k < 4; ++k) {
                            char h = s_[i_++];
                            cp <<= 4;
                            if (h >= '0' && h <= '9') cp |= (h - '0');
                            else if (h >= 'a' && h <= 'f') cp |= (h - 'a' + 10);
                            else if (h >= 'A' && h <= 'F') cp |= (h - 'A' + 10);
                            else return std::nullopt;
                        }
                        // Encode as UTF-8 (BMP only; surrogate pairs not handled for brevity).
                        if (cp < 0x80) {
                            out.push_back(static_cast<char>(cp));
                        } else if (cp < 0x800) {
                            out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
                            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                        } else {
                            out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
                            out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
                            out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
                        }
                        break;
                    }
                    default:
                        __android_log_print(ANDROID_LOG_WARN, TAG,
                                            "bad escape '\\%c'", e);
                        return std::nullopt;
                }
            } else {
                out.push_back(c);
            }
        }
        return std::nullopt;  // unterminated
    }

    std::optional<JsonValue> parseNumber() {
        size_t start = i_;
        if (i_ < s_.size() && s_[i_] == '-') ++i_;
        while (i_ < s_.size() && std::isdigit(static_cast<unsigned char>(s_[i_]))) ++i_;
        if (i_ < s_.size() && s_[i_] == '.') {
            ++i_;
            while (i_ < s_.size() && std::isdigit(static_cast<unsigned char>(s_[i_]))) ++i_;
        }
        if (i_ < s_.size() && (s_[i_] == 'e' || s_[i_] == 'E')) {
            ++i_;
            if (i_ < s_.size() && (s_[i_] == '+' || s_[i_] == '-')) ++i_;
            while (i_ < s_.size() && std::isdigit(static_cast<unsigned char>(s_[i_]))) ++i_;
        }
        std::string numStr = s_.substr(start, i_ - start);
        try {
            return JsonValue::makeNumber(std::stod(numStr));
        } catch (...) {
            return std::nullopt;
        }
    }

    std::optional<JsonValue> parseBool() {
        if (s_.compare(i_, 4, "true") == 0)  { i_ += 4; return JsonValue::makeBool(true);  }
        if (s_.compare(i_, 5, "false") == 0) { i_ += 5; return JsonValue::makeBool(false); }
        return std::nullopt;
    }

    std::optional<JsonValue> parseNull() {
        if (s_.compare(i_, 4, "null") == 0) { i_ += 4; return JsonValue::makeNull(); }
        return std::nullopt;
    }
};
} // anonymous namespace


std::optional<JsonValue> parseJson(const std::string& json) {
    Parser p(json);
    return p.parse();
}

// ============================================================
// Serializer (JsonValue -> std::string)
// ============================================================

std::string serializeJson(const JsonValue& v) {
    std::ostringstream oss;
    switch (v.type) {
        case JsonValue::Type::Null:   oss << "null"; break;
        case JsonValue::Type::Bool:   oss << (v.boolValue ? "true" : "false"); break;
        case JsonValue::Type::Number: {
            // Integral doubles serialize without decimal point for clean integers.
            double intPart;
            if (std::modf(v.numberValue, &intPart) == 0.0 &&
                std::abs(v.numberValue) < 1e18) {
                oss << static_cast<int64_t>(v.numberValue);
            } else {
                oss << v.numberValue;
            }
            break;
        }
        case JsonValue::Type::String: oss << jsonString(v.stringValue); break;
        case JsonValue::Type::Array: {
            oss << '[';
            for (size_t i = 0; i < v.arrayValue.size(); ++i) {
                if (i) oss << ',';
                oss << serializeJson(v.arrayValue[i]);
            }
            oss << ']';
            break;
        }
        case JsonValue::Type::Object: {
            oss << '{';
            for (size_t i = 0; i < v.objectValue.size(); ++i) {
                if (i) oss << ',';
                oss << jsonString(v.objectValue[i].first) << ':'
                    << serializeJson(v.objectValue[i].second);
            }
            oss << '}';
            break;
        }
    }
    return oss.str();
}

// ============================================================
// Struct serializers
// ============================================================

std::string serializeSubtask(const NativeSubtask& s) {
    std::ostringstream oss;
    oss << '{'
        << "\"id\":"          << jsonString(s.id)          << ','
        << "\"description\":" << jsonString(s.description) << ','
        << "\"status\":"      << jsonString(s.status)      << ','
        << "\"output\":"      << jsonString(s.output)
        << '}';
    return oss.str();
}

std::string serializeTask(const NativeTask& t) {
    std::ostringstream oss;
    oss << '{'
        << "\"id\":"                << jsonString(t.id)                << ','
        << "\"description\":"       << jsonString(t.description)       << ','
        << "\"preset\":"            << jsonString(t.preset)            << ','
        << "\"status\":"            << jsonString(taskStatusToString(t.status)) << ','
        << "\"createdAt\":"         << t.createdAt                     << ','
        << "\"startedAt\":"         << t.startedAt                     << ','
        << "\"completedAt\":"       << t.completedAt                   << ','
        << "\"progress\":"          << t.progress                      << ','
        << "\"result\":"            << jsonString(t.result)            << ','
        << "\"errorMessage\":"      << jsonString(t.errorMessage)      << ','
        << "\"agentInvocations\":"  << t.agentInvocations              << ','
        << "\"retryCount\":"        << t.retryCount                    << ','
        << "\"durationMs\":"        << t.durationMs
        << '}';
    return oss.str();
}

std::string serializeConfig(const NativeRageConfig& c) {
    std::ostringstream oss;
    oss << '{'
        << "\"maxConcurrency\":"      << c.maxConcurrency      << ','
        << "\"defaultTimeoutMs\":"    << c.defaultTimeoutMs    << ','
        << "\"maxRetries\":"          << c.maxRetries          << ','
        << "\"enableAutoExpand\":"    << (c.enableAutoExpand    ? "true" : "false") << ','
        << "\"enableGitBranching\":"  << (c.enableGitBranching  ? "true" : "false") << ','
        << "\"enableSandboxExec\":"   << (c.enableSandboxExec   ? "true" : "false") << ','
        << "\"enableGithubSearch\":"  << (c.enableGithubSearch  ? "true" : "false") << ','
        << "\"enableCodeRag\":"       << (c.enableCodeRag       ? "true" : "false")
        << '}';
    return oss.str();
}

std::string serializeResult(const NativeExecutionResult& r) {
    std::ostringstream oss;
    oss << '{'
        << "\"success\":"            << (r.success ? "true" : "false") << ','
        << "\"errorMessage\":"       << jsonString(r.errorMessage) << ','
        << "\"subtasks\":[";
    for (size_t i = 0; i < r.subtasks.size(); ++i) {
        if (i) oss << ',';
        oss << serializeSubtask(r.subtasks[i]);
    }
    oss << "],"
        << "\"agentInvocations\":"  << r.agentInvocations  << ','
        << "\"retryCount\":"        << r.retryCount        << ','
        << "\"durationMs\":"        << r.durationMs        << ','
        << "\"finalOutput\":"       << jsonString(r.finalOutput) << ','
        << "\"taskId\":"            << jsonString(r.taskId)
        << '}';
    return oss.str();
}

std::string serializeMetrics(const NativeMetrics& m) {
    std::ostringstream oss;
    oss << '{'
        << "\"totalTasks\":"            << m.totalTasks            << ','
        << "\"successfulTasks\":"       << m.successfulTasks       << ','
        << "\"failedTasks\":"           << m.failedTasks           << ','
        << "\"cancelledTasks\":"        << m.cancelledTasks        << ','
        << "\"averageExecutionTimeMs\":"<< m.averageExecutionTimeMs<< ','
        << "\"successRate\":"           << m.successRate           << ','
        << "\"currentConcurrency\":"    << m.currentConcurrency    << ','
        << "\"peakConcurrency\":"       << m.peakConcurrency
        << '}';
    return oss.str();
}

std::string serializeEvent(const NativeEvent& e) {
    std::ostringstream oss;
    oss << '{'
        << "\"type\":"            << jsonString(eventTypeToString(e.type)) << ','
        << "\"taskId\":"          << jsonString(e.taskId)          << ','
        << "\"progress\":"        << e.progress                    << ','
        << "\"message\":"         << jsonString(e.message)         << ','
        << "\"agentId\":"         << jsonString(e.agentId)         << ','
        << "\"agentName\":"       << jsonString(e.agentName)       << ','
        << "\"action\":"          << jsonString(e.action)          << ','
        << "\"success\":"         << (e.success ? "true" : "false")<< ','
        << "\"skillId\":"         << jsonString(e.skillId)         << ','
        << "\"skillName\":"       << jsonString(e.skillName)       << ','
        << "\"llmPrompt\":"       << jsonString(e.llmPrompt)       << ','
        << "\"llmSystemPrompt\":" << jsonString(e.llmSystemPrompt) << ','
        << "\"searchQuery\":"     << jsonString(e.searchQuery)
        << '}';
    return oss.str();
}

// ============================================================
// Struct deserializers
// ============================================================

NativeSubtask deserializeSubtask(const std::string& json) {
    NativeSubtask s;
    auto root = parseJson(json);
    if (!root || root->type != JsonValue::Type::Object) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "deserializeSubtask: parse failed");
        return s;
    }
    if (auto v = root->asString("id"))          s.id = *v;
    if (auto v = root->asString("description")) s.description = *v;
    if (auto v = root->asString("status"))      s.status = *v;
    if (auto v = root->asString("output"))      s.output = *v;
    return s;
}

NativeTask deserializeTask(const std::string& json) {
    NativeTask t;
    auto root = parseJson(json);
    if (!root || root->type != JsonValue::Type::Object) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "deserializeTask: parse failed");
        return t;
    }
    if (auto v = root->asString("id"))          t.id = *v;
    if (auto v = root->asString("description")) t.description = *v;
    if (auto v = root->asString("preset"))      t.preset = *v;
    if (auto v = root->asString("status"))      t.status = taskStatusFromString(*v);
    if (auto v = root->asInt("createdAt"))      t.createdAt = *v;
    if (auto v = root->asInt("startedAt"))      t.startedAt = *v;
    if (auto v = root->asInt("completedAt"))    t.completedAt = *v;
    if (auto v = root->asNumber("progress"))    t.progress = static_cast<float>(*v);
    if (auto v = root->asString("result"))      t.result = *v;
    if (auto v = root->asString("errorMessage"))t.errorMessage = *v;
    if (auto v = root->asInt("agentInvocations")) t.agentInvocations = static_cast<int>(*v);
    if (auto v = root->asInt("retryCount"))     t.retryCount = static_cast<int>(*v);
    if (auto v = root->asInt("durationMs"))     t.durationMs = *v;
    return t;
}

NativeRageConfig deserializeConfig(const std::string& json) {
    NativeRageConfig c;
    auto root = parseJson(json);
    if (!root || root->type != JsonValue::Type::Object) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "deserializeConfig: parse failed");
        return c;
    }
    if (auto v = root->asInt("maxConcurrency"))    c.maxConcurrency = static_cast<int>(*v);
    if (auto v = root->asInt("defaultTimeoutMs"))  c.defaultTimeoutMs = *v;
    if (auto v = root->asInt("maxRetries"))        c.maxRetries = static_cast<int>(*v);
    if (auto v = root->asBool("enableAutoExpand"))   c.enableAutoExpand = *v;
    if (auto v = root->asBool("enableGitBranching")) c.enableGitBranching = *v;
    if (auto v = root->asBool("enableSandboxExec"))  c.enableSandboxExec = *v;
    if (auto v = root->asBool("enableGithubSearch")) c.enableGithubSearch = *v;
    if (auto v = root->asBool("enableCodeRag"))      c.enableCodeRag = *v;
    return c;
}

NativeExecutionResult deserializeResult(const std::string& json) {
    NativeExecutionResult r;
    auto root = parseJson(json);
    if (!root || root->type != JsonValue::Type::Object) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "deserializeResult: parse failed");
        return r;
    }
    if (auto v = root->asBool("success"))          r.success = *v;
    if (auto v = root->asString("errorMessage"))   r.errorMessage = *v;
    if (auto v = root->asInt("agentInvocations"))  r.agentInvocations = static_cast<int>(*v);
    if (auto v = root->asInt("retryCount"))        r.retryCount = static_cast<int>(*v);
    if (auto v = root->asInt("durationMs"))        r.durationMs = *v;
    if (auto v = root->asString("finalOutput"))    r.finalOutput = *v;
    if (auto v = root->asString("taskId"))         r.taskId = *v;
    if (auto arr = root->asArray("subtasks")) {
        for (const auto& item : *arr) {
            if (item.type != JsonValue::Type::Object) continue;
            NativeSubtask st;
            if (auto v = item.asString("id"))          st.id = *v;
            if (auto v = item.asString("description")) st.description = *v;
            if (auto v = item.asString("status"))      st.status = *v;
            if (auto v = item.asString("output"))      st.output = *v;
            r.subtasks.push_back(std::move(st));
        }
    }
    return r;
}

NativeMetrics deserializeMetrics(const std::string& json) {
    NativeMetrics m;
    auto root = parseJson(json);
    if (!root || root->type != JsonValue::Type::Object) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "deserializeMetrics: parse failed");
        return m;
    }
    if (auto v = root->asInt("totalTasks"))            m.totalTasks = *v;
    if (auto v = root->asInt("successfulTasks"))       m.successfulTasks = *v;
    if (auto v = root->asInt("failedTasks"))           m.failedTasks = *v;
    if (auto v = root->asInt("cancelledTasks"))        m.cancelledTasks = *v;
    if (auto v = root->asNumber("averageExecutionTimeMs")) m.averageExecutionTimeMs = *v;
    if (auto v = root->asNumber("successRate"))        m.successRate = *v;
    if (auto v = root->asInt("currentConcurrency"))    m.currentConcurrency = static_cast<int>(*v);
    if (auto v = root->asInt("peakConcurrency"))       m.peakConcurrency = static_cast<int>(*v);
    return m;
}

NativeEvent deserializeEvent(const std::string& json) {
    NativeEvent e;
    auto root = parseJson(json);
    if (!root || root->type != JsonValue::Type::Object) {
        __android_log_print(ANDROID_LOG_WARN, TAG, "deserializeEvent: parse failed");
        return e;
    }
    if (auto v = root->asString("type"))            e.type = eventTypeFromString(*v);
    if (auto v = root->asString("taskId"))          e.taskId = *v;
    if (auto v = root->asNumber("progress"))        e.progress = static_cast<float>(*v);
    if (auto v = root->asString("message"))         e.message = *v;
    if (auto v = root->asString("agentId"))         e.agentId = *v;
    if (auto v = root->asString("agentName"))       e.agentName = *v;
    if (auto v = root->asString("action"))          e.action = *v;
    if (auto v = root->asBool("success"))           e.success = *v;
    if (auto v = root->asString("skillId"))         e.skillId = *v;
    if (auto v = root->asString("skillName"))       e.skillName = *v;
    if (auto v = root->asString("llmPrompt"))       e.llmPrompt = *v;
    if (auto v = root->asString("llmSystemPrompt")) e.llmSystemPrompt = *v;
    if (auto v = root->asString("searchQuery"))     e.searchQuery = *v;
    return e;
}

} // namespace rage::native
