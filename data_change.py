import pandas as pd
import os

def convert_dutir_to_dict(excel_path, output_dir):
    """
    将大连理工大学情感词汇本体库 Excel 转换为 SentimentAnalyzer 所需词典
    :param excel_path: 本体库 Excel 文件路径
    :param output_dir: 输出目录 (建议为 src/main/resources/dict/)
    """
    # 读取 Excel
    df = pd.read_excel(excel_path)

    # 检查必需列是否存在
    required_cols = ['词语', '极性']
    for col in required_cols:
        if col not in df.columns:
            raise ValueError(f"Excel 文件中缺少 '{col}' 列，请确认文件格式")

    # 提取积极词 (极性 == 1)
    pos_words = df[df['极性'] == 1]['词语'].dropna().unique()
    # 提取消极词 (极性 == 2)
    neg_words = df[df['极性'] == 2]['词语'].dropna().unique()

    # 确保输出目录存在
    os.makedirs(output_dir, exist_ok=True)

    # 写入文件 (UTF-8 编码，每行一个词)
    pos_path = os.path.join(output_dir, 'pos_words.txt')
    neg_path = os.path.join(output_dir, 'neg_words.txt')

    with open(pos_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(pos_words))

    with open(neg_path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(neg_words))

    print(f"✅ 转换完成！")
    print(f"积极词数量: {len(pos_words)} → {pos_path}")
    print(f"消极词数量: {len(neg_words)} → {neg_path}")
    print("\n⚠️ 注意：程度副词 (degree_words.txt) 和否定词 (not_words.txt) 需要您自行准备。")

if __name__ == "__main__":
    # 请修改为您的实际文件路径
    excel_file = "情感词汇本体.xlsx"
    # 输出到项目的 resources/dict 目录 (请根据实际项目路径调整)
    output_directory = "src/main/resources/dict"

    convert_dutir_to_dict(excel_file, output_directory)