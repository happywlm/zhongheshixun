/**
 * 校验 docx 的内容完整性:对比源 md 的 表格数 / 标题数 / 段落文本
 */
const fs = require('fs');
const path = require('path');
const { execSync } = require('child_process');

const SRC = path.join(__dirname, '..', 'docs', '系统设计说明书.md');
const OUT = path.join(__dirname, '..', '系统设计说明书.docx');
const md = fs.readFileSync(SRC, 'utf8');

// 统计源 md
const mdLines = md.split('\n');
const mdH1 = mdLines.filter(l => /^# \s/.test(l) || /^##\s/.test(l)).length;
const mdTitles = mdLines.filter(l => /^#{1,4}\s/.test(l)).length;
const mdTables = (() => {
  let t = 0, inT = false;
  for (const l of mdLines) {
    if (l.trim().startsWith('|')) { if (!inT) { t++; inT = true; } }
    else inT = false;
  }
  return t;
})();
const mdCodeBlocks = (md.match(/```/g) || []).length / 2;
const mdChars = md.length;

console.log('=== 源 md 统计 ===');
console.log('标题数:', mdTitles, '| 表格数:', mdTables, '| 代码块数:', mdCodeBlocks, '| 字符数:', mdChars);

// 提取 docx 文本
const tmpDir = path.join(__dirname, '.verify-tmp');
if (!fs.existsSync(tmpDir)) fs.mkdirSync(tmpDir);
execSync(`python "${path.join(__dirname, '../../AppData/Local/Claude-3p/local-agent-mode-sessions/skills-plugin/00000000-0000-4000-8000-000000000001/b54dc338-90fb-458b-9e1e-b7285ff3adf3/skills/docx/scripts/office/unpack.py")}" "${OUT}" "${tmpDir}"`, { stdio: 'pipe' });

const docXml = fs.readFileSync(path.join(tmpDir, 'word/document.xml'), 'utf8');
// 剥离 XML 标签得纯文本
const textOnly = docXml.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ');

console.log('\n=== docx 生成结果 ===');
console.log('文件大小:', (fs.statSync(OUT).size / 1024).toFixed(1), 'KB');
console.log('docx 文本字符数:', textOnly.length);
console.log('文本保留率:', ((textOnly.length / mdChars) * 100).toFixed(1) + '%');

// 标题保留率
let titleHit = 0;
const titleTexts = mdLines.filter(l => /^#{1,4}\s/.test(l)).map(l => l.replace(/^#{1,4}\s/, '').trim());
titleTexts.forEach(t => { if (textOnly.includes(t)) titleHit++; });
console.log('标题保留:', titleHit, '/', titleTexts.length);

// 关键章节存在性
const chapters = ['引言', '项目概述', '原型设计说明', '数据库设计', '系统设计', '映射关系', '设计优化报告', '结语'];
console.log('\n=== 关键章节存在性 ===');
chapters.forEach(c => console.log(c + ':', textOnly.includes(c) ? '✅' : '❌'));

// 关键事实(一致性)
console.log('\n=== 事实验证 ===');
['123456', '9898', '9899', 'FR-EXAM', 'consult_record', '自动阅卷', 'MapReduce'].forEach(k => {
  console.log(k + ':', textOnly.includes(k) ? '✅' : '❌');
});

// 清理
execSync(`rmdir /s /q "${tmpDir}"`);
