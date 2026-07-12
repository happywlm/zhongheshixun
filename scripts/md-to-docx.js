/**
 * md-to-docx: 把 docs/系统设计说明书.md 转为 系统设计说明书.docx
 * 轻量实现,不依赖重型解析器;逐行状态机处理:标题/表格/代码块/段落/列表
 */
const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  AlignmentType, HeadingLevel, BorderStyle, WidthType, ShadingType,
  LevelFormat, PageBreak, PageNumber, Header, Footer, TabStopType,
  TabStopPosition
} = require('docx');

const SRC = path.join(__dirname, '..', 'docs', '系统设计说明书.md');
const OUT = path.join(__dirname, '..', '系统设计说明书.docx');

// ---------- A4 页面(2.5cm 边距) ----------
const PAGE_W = 11906; const PAGE_H = 16838; // DXA
const MARGIN = 1417;                        // 2.5cm ≈ 1417 DXA
const CONTENT_W = PAGE_W - MARGIN * 2;      // ≈ 9072 DXA

// ---------- 样式辅助 ----------
const border = { style: BorderStyle.SINGLE, size: 1, color: '999999' };
const borders = { top: border, bottom: border, left: border, right: border };
const cellMargins = { top: 60, bottom: 60, left: 100, right: 100 };

function p(text, opts = {}) {
  const { bold = false, size = 24, font = '宋体', color, align, spacing } = opts;
  return new Paragraph({
    alignment: align,
    spacing: spacing || { after: 80, line: 360 },
    children: [new TextRun({ text, bold, size, font, color,
      font: { name: font, hint: 'eastAsia' } })]
  });
}
function h1(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_1, alignment: AlignmentType.CENTER,
    spacing: { before: 300, after: 200 },
    children: [new TextRun({ text, bold: true, size: 36, font: '黑体',
      font: { name: '黑体', hint: 'eastAsia' } })]
  });
}
function h2(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_2,
    spacing: { before: 240, after: 160 },
    children: [new TextRun({ text, bold: true, size: 32, font: '黑体',
      font: { name: '黑体', hint: 'eastAsia' } })]
  });
}
function h3(text) {
  return new Paragraph({
    heading: HeadingLevel.HEADING_3,
    spacing: { before: 200, after: 120 },
    children: [new TextRun({ text, bold: true, size: 28, font: '黑体',
      font: { name: '黑体', hint: 'eastAsia' } })]
  });
}
function richP(runs, opts = {}) {
  const { align, spacing } = opts;
  return new Paragraph({
    alignment: align,
    spacing: spacing || { after: 80, line: 360 },
    children: runs
  });
}
function run(text, opts = {}) {
  const { bold = false, size = 24, font = '宋体', color, italics } = opts;
  return new TextRun({ text, bold, size, italics, color,
    font: { name: font, hint: 'eastAsia' } });
}

// 一个 markdown 单元格文本 → 多个 Paragraph(含粗体/**xxx**/处理)
function cellParas(text, opts = {}) {
  const { bold: cellBold = false, header = false } = opts;
  const paras = [];
  const lines = text.replace(/<br\s*\>/gi, '\n').split('\n');
  lines.forEach((ln) => {
    const runs = [];
    // 处理 **bold** 与 `code` 与 [1] 等
    const re = /(\*\*(.+?)\*\*)|(`([^`]+)`)|([^`*]+)/g;
    let m;
    const s = ln.trim() === '' ? ' ' : ln;
    while ((m = re.exec(s)) !== null) {
      if (m[2]) runs.push(run(m[2], { bold: true, size: 21, font: '宋体' }));
      else if (m[4]) runs.push(run(m[4], { size: 20, font: 'Courier New', color: '333333' }));
      else if (m[5]) runs.push(run(m[5], { bold: cellBold || header, size: 21, font: '宋体' }));
    }
    if (runs.length === 0) runs.push(run(' ', { size: 21 }));
    paras.push(new Paragraph({ spacing: { after: 40, line: 300 }, children: runs }));
  });
  return paras;
}

// markdown 表格 → Word 表
function mdTableToWord(lines) {
  // lines: 含表头行 + 对齐行 + 数据行
  const rows = lines.filter(l => l.trim().startsWith('|'));
  const headerLine = rows[0];
  const dataLines = rows.slice(1); // 跳过对齐行
  const splitRow = (line) => line.split('|').slice(1, -1).map(c => c.trim());
  const headers = splitRow(headerLine);
  const n = headers.length;
  // 计算列宽:均匀分(也可按字数加权,均匀最稳)
  const colW = Math.floor(CONTENT_W / n);
  const colWidths = Array(n).fill(colW);

  const makeCell = (text, isHeader) => new TableCell({
    borders,
    width: { size: colW, type: WidthType.DXA },
    margins: cellMargins,
    shading: isHeader ? { fill: 'E8E8E8', type: ShadingType.CLEAR } : undefined,
    children: cellParas(text, { header: isHeader, bold: isHeader })
  });

  const wRows = [];
  wRows.push(new TableRow({ children: headers.map(h => makeCell(h, true)) }));
  dataLines.forEach(dl => {
    const cells = splitRow(dl);
    while (cells.length < n) cells.push('');
    wRows.push(new TableRow({ children: cells.map(c => makeCell(c, false)) }));
  });

  return new Table({
    width: { size: CONTENT_W, type: WidthType.DXA },
    columnWidths: colWidths,
    rows: wRows
  });
}

// ---------- 主解析 ----------
const md = fs.readFileSync(SRC, 'utf8');
const lines = md.split('\n');

const children = [];
children.push(h1('系统设计说明书(含设计优化报告)'));

let i = 0;
let tableBlock = [];
let inCode = false;
let codeLang = '';
let codeBuf = [];
let listBuf = [];
let bodyBuf = [];

const flushBody = () => {
  if (bodyBuf.length) {
    children.push(richP([run(bodyBuf.join(' '), { size: 24 })]));
    bodyBuf.length = 0;
  }
};
const flushList = () => {
  listBuf.forEach(it => {
    children.push(new Paragraph({
      numbering: { reference: 'bullets', level: 0 },
      children: [run(it, { size: 24 })]
    }));
  });
  listBuf.length = 0;
};
const flushTable = () => {
  if (tableBlock.length >= 2) {
    children.push(mdTableToWord(tableBlock));
    children.push(p('')); // 空行
  }
  tableBlock.length = 0;
};

while (i < lines.length) {
  const line = lines[i];

  // 代码块
  if (/^```/.test(line)) {
    flushBody(); flushList(); flushTable();
    if (!inCode) { inCode = true; codeLang = line.replace('```', '').trim() || 'text'; codeBuf = []; }
    else {
      const isJava = /java|json/i.test(codeLang);
      const fontName = isJava ? 'Courier New' : 'Consolas';
      children.push(new Paragraph({
        spacing: { before: 120, after: 120 },
        shading: { fill: 'F5F5F5', type: ShadingType.CLEAR },
        border: { top: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDB' },
                  bottom: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDB' },
                  left: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDB' },
                  right: { style: BorderStyle.SINGLE, size: 1, color: 'DDDDDB' } },
        children: [run(`[${codeLang}]`, { size: 20, font: fontName, color: '888888', italics: true })]
      }));
      codeBuf.forEach(c => {
        children.push(new Paragraph({
          spacing: { after: 20, line: 300 },
          shading: { fill: 'F5F5F5', type: ShadingType.CLEAR },
          children: [run(c === '' ? ' ' : c, { size: 20, font: fontName, color: '222222' })]
        }));
      });
      inCode = false; codeLang = ''; codeBuf = [];
    }
    i++; continue;
  }
  if (inCode) { codeBuf.push(line); i++; continue; }

  // 表格行
  if (line.trim().startsWith('|')) {
    flushBody(); flushList();
    tableBlock.push(line);
    i++; continue;
  } else flushTable();

  // 标题
  if (/^####\s/.test(line)) { flushBody(); flushList(); children.push(h3(line.replace('#### ', ''))); i++; continue; }
  else if (/^###\s/.test(line)) { flushBody(); flushList(); children.push(h2(line.replace('### ', ''))); i++; continue; }
  else if (/^##\s/.test(line)) { flushBody(); flushList(); children.push(h1(line.replace(/^## /, ''), )); i++; continue; }
  else if (/^#\s/.test(line)) { flushBody(); flushList(); children.push(h1(line.replace('# ', ''))); i++; continue; }

  // 分隔线
  if (/^---+\s*$/.test(line)) { flushBody(); flushList(); i++; continue; }

  // 引用块
  if (/^>\s/.test(line)) {
    flushBody(); flushList();
    children.push(richP([run(line.replace(/^>\s/, ''), { size: 24, color: '555555', italics: true })],
      { spacing: { after: 100 } }));
    i++; continue;
  }

  // 列表项(- 或 * 或 数字.)
  const lm = line.match(/^(\s*)([-*]|\d+\.)\s+(.*)$/);
  if (lm) {
    flushBody();
    listBuf.push(lm[3]);
    i++; continue;
  } else flushList();

  // 空行 → 段落分隔
  if (line.trim() === '') { flushBody(); i++; continue; }

  // 普通段落(累积到 bodyBuf 处理软换行)
  bodyBuf.push(line.trim());
  i++;
}
flushBody(); flushList(); flushTable();

// ---------- 文档 ----------
const doc = new Document({
  numbering: {
    config: [{
      reference: 'bullets',
      levels: [{ level: 0, format: LevelFormat.BULLET, text: '•',
        alignment: AlignmentType.LEFT,
        style: { paragraph: { indent: { left: 720, hanging: 360 } } } }]
    }]
  },
  sections: [{
    properties: {
      page: {
        size: { width: PAGE_W, height: PAGE_H },
        margin: { top: MARGIN, right: MARGIN, bottom: MARGIN, left: MARGIN }
      }
    },
    headers: {
      default: new Header({ children: [new Paragraph({
        alignment: AlignmentType.CENTER,
        children: [run('系统设计说明书', { size: 18, color: '999999' })]
      })] })
    },
    footers: {
      default: new Footer({ children: [new Paragraph({
        alignment: AlignmentType.CENTER,
        children: [run('- ', { size: 18 }), run({ children: [PageNumber.CURRENT], size: 18 }), run(' -', { size: 18 })]
      })] })
    },
    children
  }]
});

Packer.toBuffer(doc).then(buf => {
  fs.writeFileSync(OUT, buf);
  console.log('生成完成:', OUT);
  console.log('文件大小:', (buf.length / 1024 / 1024).toFixed(2), 'MB');
}).catch(err => { console.error('生成失败:', err); process.exit(1); });
