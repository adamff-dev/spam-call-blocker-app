# Spam Call Blocker

**Language:** [English](./README.md) | **日本語**

Spam Call Blocker は Kotlin で開発されたアプリで、リアルタイムな API のチェックと複数の国に対応、その他の効果的な通話フィルタリングの手法を組み合わせることで、迷惑電話をブロックできます。

## スクリーンショット

Spam Call Blocker を使用中のスクリーンショットをいくつかご紹介します:

<div style="display: flex; gap: 10px;">
  <img src="https://i.imgur.com/g8Ie7zU.png" alt="メイン画面" width="300">  
  <img src="https://i.imgur.com/QnbalOh.png" alt="電話番号オプション" width="300">  
  <img src="https://i.imgur.com/QeC3Kg8.png" alt="設定" width="300">  
  <img src="https://i.imgur.com/I4tDolO.png" alt="その他の設定" width="300">
  <img src="https://i.imgur.com/T2DdbPY.png" alt="報告時のダイアログ" width="300">
</div>

## 機能

- **電話番号をブロック、ブロックを解除**: 特定の電話番号を簡単にブロックまたはブロックを解除できます。
- **ホワイトリストの管理**: 常に連絡を許可する電話番号のホワイトリストを作成と管理が行えます。
- **高度なブロックオプション**: 複数のブロックする基準が利用可能です:
  - 非通知の電話番号をブロック
  - 連絡先に未登録の電話番号の着信をブロック
  - 国際電話の着信をブロック

- **国際電話番号の検索**: 複数のソースで検証できます:
  - UnknownPhone API
  - Tellows API
  - TrueCaller API
- **STIR/SHAKEN 認証**: STIR/SHAKEN レベルに基づいた通話ブロック機能に対応。
- **自動ミュートオプション**: 迷惑電話を完全にブロックせずに自動でミュートにするオプションが選択できます。
- **報告ダイアログ**: 不審な電話番号や安全な電話番号を報告するシステムを内蔵。
- **アプリを自動更新**: アプリの更新を自動で確認し、利用可能な更新があった場合にユーザーにインストールを促します。

## 貢献

コード、ドキュメント、バグ報告、機能の提案など様々な貢献を歓迎します。

このプロジェクトが役立った、発展を支援したいと感じたのであれば、寄付をご検討ください。

皆様のご支援のおかげで、このプロジェクトの継続と維持がされています。ありがとうございます！ 🙌

<div style="display: flex; gap: 10px;">
  <a target="_blank" href="https://www.buymeacoffee.com/rSiZtB3"><img style="height: 50px" src="https://i.imgur.com/KCk0bxY.png" /></a>
  <a target="_blank" href="https://www.paypal.com/donate/?hosted_button_id=3T9XNAPWW36Z2"><img style="height: 50px" src="https://i.imgur.com/Z3x38ey.png" /></a>
</div>

## 仮想通貨による寄付

仮想通貨でプロジェクトを支援することもできます:

- **Bitcoin:** `bc1qrcdyq2yjgv5alm9kky2e6vyfhnafn3wgd2gjls`
- **Ethereum:** `0x43b9649985d6789452abe23beb1eb610cee88817`
- **Solana:** `4qK7eSQemRj85VY9CQp5XHRwX5fNjoSJ1ou4gmqk6jtM`
- **Litecoin:** `ltc1qp6mya23a73n36dc7r0tfwfphn2v53phmhen99j`

## データの管理

- **エクスポート**: 設定や電話番号のブラックリスト/ホワイトリストなど、すべての設定をファイルにエクスポートでバックアップや別のデバイスに転送ができます。
- **インポート**: 以前にエクスポートしたデータをインポートで、新しいデバイスにアプリを素早くセットアップや、リセット後に設定を復元できます。

## プライバシーとセキュリティ

このアプリは、ユーザーのプライバシー保護を最優先に設計されています。すべてのデータはデバイスにローカルに保存され、アプリがインターネットにアクセスするのは電話番号検索を行う API 要求を実行する場合のみです。個人データを外部サーバーに共有することは一切ありません。

## 要件

- Android 10.0 またはそれ以上
- API 要求でのインターネット接続

## インストール

1. Release ページから APK ファイルをダウンロードか、自分でコンパイルしてください。
2. デバイスの設定で提供元不明のアプリのインストールを有効化してください。
3. アプリをインストールし、通話の管理とインターネットアクセスに必要な権限を付与してください。

## ライセンス

このアプリは GPLv3 に基づいてリリースされています。詳細については LICENSE ファイルを参照してください。
