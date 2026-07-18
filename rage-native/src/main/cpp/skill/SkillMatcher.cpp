#include "skill/SkillMatcher.h"

#include <algorithm>
#include <cctype>
#include <sstream>
#include <unordered_map>
#include <unordered_set>

namespace rage::native {

namespace {

std::string toLower(const std::string& s) {
    std::string out;
    out.reserve(s.size());
    for (char c : s) out.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
    return out;
}

std::vector<std::string> tokenize(const std::string& s) {
    std::vector<std::string> out;
    std::string cur;
    for (char c : s) {
        if (std::isalnum(static_cast<unsigned char>(c)) || c == '_' || c == '-' || c == '.') {
            cur.push_back(static_cast<char>(std::tolower(static_cast<unsigned char>(c))));
        } else {
            if (!cur.empty()) { out.push_back(cur); cur.clear(); }
        }
    }
    if (!cur.empty()) out.push_back(cur);
    return out;
}

} // namespace

std::vector<std::string> SkillMatcher::match(const std::string& taskDescription,
                                             const SkillGraph& graph) const {
    auto tokens = tokenize(taskDescription);
    if (tokens.empty()) return {};

    std::unordered_set<std::string> tokenSet(tokens.begin(), tokens.end());

    std::vector<std::pair<std::string, int>> scored;
    for (const auto& id : graph.all()) {
        const SkillNode* node = graph.find(id);
        if (!node) continue;

        std::string idLower   = toLower(node->id);
        std::string nameLower = toLower(node->name);
        std::string catLower  = toLower(node->category);
        std::string descLower = toLower(node->description);

        int score = 0;
        for (const auto& t : tokenSet) {
            if (idLower.find(t)   != std::string::npos) score += 3;
            if (nameLower.find(t) != std::string::npos) score += 2;
            if (catLower.find(t)  != std::string::npos) score += 2;
            if (descLower.find(t) != std::string::npos) score += 1;
        }
        if (score > 0) scored.emplace_back(id, score);
    }

    std::sort(scored.begin(), scored.end(),
              [](const auto& a, const auto& b) {
                  if (a.second != b.second) return a.second > b.second;
                  return a.first < b.first;
              });

    std::vector<std::string> result;
    result.reserve(scored.size());
    for (const auto& [id, _] : scored) result.push_back(id);
    return result;
}

} // namespace rage::native
