#!/usr/bin/env bash
# ============================================================
# 生成 Apex 签名 keystore + 配置 GitHub Secrets 指南
# ============================================================
# 用法：
#   ./scripts/generate-keystore.sh
#
# 生成后请将 4 个值配置到 GitHub repo Settings → Secrets and variables → Actions:
#   SIGNING_KEYSTORE         = base64 编码的 .jks 文件内容
#   SIGNING_STORE_PASSWORD   = keystore 密码
#   SIGNING_KEY_ALIAS        = key alias（默认 apex）
#   SIGNING_KEY_PASSWORD     = key 密码
# ============================================================
set -euo pipefail

KEYSTORE_NAME="apex-release.jks"
ALIAS="apex"
VALIDITY=36500  # 100 years
STORE_PASS=""
KEY_PASS=""

echo "🔑 Apex AI Agent 签名密钥生成工具"
echo "================================"
echo ""

# 交互式输入密码
read -s -p "请输入 keystore 密码 (至少 6 位): " STORE_PASS
echo ""
if [ ${#STORE_PASS} -lt 6 ]; then
  echo "❌ 密码至少 6 位"
  exit 1
fi
read -s -p "请再次输入 keystore 密码: " STORE_PASS_CONFIRM
echo ""
if [ "$STORE_PASS" != "$STORE_PASS_CONFIRM" ]; then
  echo "❌ 两次密码不一致"
  exit 1
fi
KEY_PASS="$STORE_PASS"

echo ""
echo "📝 输入签名信息（可留空使用默认值）："
read -p "组织名称 [Apex AI]: " ORG
ORG=${ORG:-"Apex AI"}
read -p "组织单位 [Apex Dev]: " ORG_UNIT
ORG_UNIT=${ORG_UNIT:-"Apex Dev"}
read -p "城市/地区 [Beijing]: " LOCALITY
LOCALITY=${LOCALITY:-"Beijing"}
read -p "省/州 [Beijing]: " STATE
STATE=${STATE:-"Beijing"}
read -p "国家代码 (2位) [CN]: " COUNTRY
COUNTRY=${COUNTRY:-"CN"}

echo ""
echo "🔨 生成 keystore: $KEYSTORE_NAME"
keytool -genkeypair \
  -alias "$ALIAS" \
  -keyalg RSA \
  -keysize 2048 \
  -validity $VALIDITY \
  -keystore "$KEYSTORE_NAME" \
  -storepass "$STORE_PASS" \
  -keypass "$KEY_PASS" \
  -dname "CN=Apex Agent, OU=$ORG_UNIT, O=$ORG, L=$LOCALITY, ST=$STATE, C=$COUNTRY" \
  -storetype JKS

echo ""
echo "✅ Keystore 生成成功！"
ls -la "$KEYSTORE_NAME"

echo ""
echo "========================================"
echo "📋 配置 GitHub Secrets 指南"
echo "========================================"
echo ""
echo "1. 打开仓库 Settings → Secrets and variables → Actions"
echo "2. 点击 \"New repository secret\"，添加以下 4 个 secret："
echo ""
echo "   ┌─────────────────────────────┬──────────────────────────────────────────────┐"
echo "   │ Secret Name                 │ Value                                        │"
echo "   ├─────────────────────────────┼──────────────────────────────────────────────┤"
echo "   │ SIGNING_KEYSTORE            │ $(base64 -w0 "$KEYSTORE_NAME" | head -c 60)... │"
echo "   │ SIGNING_STORE_PASSWORD      │ $STORE_PASS                                    │"
echo "   │ SIGNING_KEY_ALIAS           │ $ALIAS                                         │"
echo "   │ SIGNING_KEY_PASSWORD        │ $KEY_PASS                                      │"
echo "   └─────────────────────────────┴──────────────────────────────────────────────┘"
echo ""
echo "   获取完整的 base64 值："
echo "   base64 -w0 $KEYSTORE_NAME"
echo ""
echo "3. 配置完成后，推送 tag 即可触发构建："
echo "   git tag v1.0.0"
echo "   git push origin v1.0.0"
echo ""
echo "4. 或在 Actions 页面手动触发："
echo "   Actions → Build & Release APK → Run workflow"
echo ""
echo "⚠️  请妥善保管 $KEYSTORE_NAME 文件，丢失后无法更新已发布的 APK！"
echo "⚠️  切勿将 keystore 文件提交到 git！"
