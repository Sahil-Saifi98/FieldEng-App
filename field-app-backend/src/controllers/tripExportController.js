const Trip = require('../models/Trip');
const PDFDocument = require('pdfkit');
const axios = require('axios');
const fs = require('fs');

// ── Palette — charcoal/slate, no blue ────────────────────────────
const COLOR = {
  dark:      '#2C2C2C',
  darkLite:  '#EBEBEB',
  midGrey:   '#555555',
  labelBg:   '#EFEFEF',
  rowAlt:    '#F5F5F5',
  totalHl:   '#E8E8E8',
  secBadge:  '#D0D0D0',
  sigBg:     '#F8F8F8',
  border:    '#BBBBBB',
  liteTxt:   '#888888',
  greyTxt:   '#555555',
  white:     '#FFFFFF',
  black:     '#000000',
  greenBg:   '#E8F5E9',  greenBdr: '#4CAF50',  greenTxt: '#1B5E20',
  redBg:     '#FFEBEE',  redBdr:   '#F44336',  redTxt:   '#B71C1C',
  ambBg:     '#FFF8E1',  ambBdr:   '#FFB300',  ambTxt:   '#E65100',
};

const M      = 38;
const PW     = 595 - M * 2;
const ROW_H  = 17;
const HEAD_H = 19;
const SEC_H  = 18;

const CW = { sr: 20, detail: 155, from: 82, to: 70, mode: 55, days: 30 };
CW.amt = PW - CW.sr - CW.detail - CW.from - CW.to - CW.mode - CW.days;

const CX = { sr: M };
['detail','from','to','mode','days','amt'].reduce((acc, k) => {
  const prev = Object.keys(CX).slice(-1)[0];
  CX[k] = CX[prev] + CW[prev];
  return CX;
}, CX);

function fmtRs(n) {
  n = Number(n) || 0;
  const dec = n.toFixed(2).split('.')[1];
  const intStr = Math.floor(Math.abs(n)).toString();
  let formatted;
  if (intStr.length <= 3) {
    formatted = intStr;
  } else {
    const last3 = intStr.slice(-3);
    const rest = intStr.slice(0, -3);
    const groups = [];
    for (let i = rest.length; i > 0; i -= 2) groups.unshift(rest.slice(Math.max(0, i-2), i));
    formatted = groups.join(',') + ',' + last3;
  }
  return `Rs.${formatted}.${dec}`;
}

function fmtDate(d) {
  if (!d) return '';
  return new Date(d).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

function fillRect(doc, x, y, w, h, color) {
  doc.save().rect(x, y, w, h).fill(color).restore();
}

function strokeRect(doc, x, y, w, h, color = COLOR.border, lw = 0.5) {
  doc.save().rect(x, y, w, h).lineWidth(lw).stroke(color).restore();
}

function hLine(doc, x, y, w, color = COLOR.border, lw = 0.5) {
  doc.save().moveTo(x, y).lineTo(x + w, y).lineWidth(lw).stroke(color).restore();
}

function vLine(doc, x, y1, y2, color = COLOR.border, lw = 0.5) {
  doc.save().moveTo(x, y1).lineTo(x, y2).lineWidth(lw).stroke(color).restore();
}

function drawText(doc, text, x, y, { font = 'Helvetica', size = 8, color = COLOR.black,
  align = 'left', width = null } = {}) {
  doc.font(font).fontSize(size).fillColor(color);
  text = String(text ?? '');
  if (align === 'right' && width)        doc.text(text, x, y, { width, align: 'right',  lineBreak: false });
  else if (align === 'center' && width)  doc.text(text, x, y, { width, align: 'center', lineBreak: false });
  else                                   doc.text(text, x, y, { lineBreak: false });
}

function colDividers(doc, yTop, h) {
  ['detail','from','to','mode','days','amt'].forEach(k => {
    vLine(doc, CX[k], yTop, yTop + h, COLOR.border, 0.35);
  });
}

// ── Per-section column headers ────────────────────────────────────
const SECTION_COLS = {
  'Air / Train / Bus': [
    { label: 'SR',           key: 'sr',     align: 'center' },
    { label: 'DETAILS',      key: 'detail', align: 'left'   },
    { label: 'FROM',         key: 'from',   align: 'left'   },
    { label: 'TO',           key: 'to',     align: 'left'   },
    { label: 'MODE',         key: 'mode',   align: 'left'   },
    { label: 'DAYS',         key: 'days',   align: 'center' },
    { label: 'AMOUNT (Rs.)', key: 'amt',    align: 'right'  },
  ],
  'Hotel / Lodging': [
    { label: 'SR',           key: 'sr',     align: 'center' },
    { label: 'HOTEL / PLACE',key: 'detail', align: 'left'   },
    { label: 'CHECK-IN',     key: 'from',   align: 'left'   },
    { label: 'NIGHTS',       key: 'to',     align: 'left'   },
    { label: 'TYPE',         key: 'mode',   align: 'left'   },
    { label: 'NIGHTS',       key: 'days',   align: 'center' },
    { label: 'AMOUNT (Rs.)', key: 'amt',    align: 'right'  },
  ],
  'Local Conveyance': [
    { label: 'SR',           key: 'sr',     align: 'center' },
    { label: 'PURPOSE',      key: 'detail', align: 'left'   },
    { label: 'FROM',         key: 'from',   align: 'left'   },
    { label: 'TO',           key: 'to',     align: 'left'   },
    { label: 'VEHICLE TYPE', key: 'mode',   align: 'left'   },
    { label: 'TRIPS',        key: 'days',   align: 'center' },
    { label: 'AMOUNT (Rs.)', key: 'amt',    align: 'right'  },
  ],
  'Daily Allowance': [
    { label: 'SR',           key: 'sr',     align: 'center' },
    { label: 'CITY / PLACE', key: 'detail', align: 'left'   },
    { label: 'FROM DATE',    key: 'from',   align: 'left'   },
    { label: 'TO DATE',      key: 'to',     align: 'left'   },
    { label: 'RATE/DAY',     key: 'mode',   align: 'left'   },
    { label: 'DAYS',         key: 'days',   align: 'center' },
    { label: 'AMOUNT (Rs.)', key: 'amt',    align: 'right'  },
  ],
  'Other Expenses': [
    { label: 'SR',           key: 'sr',     align: 'center' },
    { label: 'DESCRIPTION',  key: 'detail', align: 'left'   },
    { label: 'PARTICULARS',  key: 'from',   align: 'left'   },
    { label: 'REFERENCE',    key: 'to',     align: 'left'   },
    { label: 'CATEGORY',     key: 'mode',   align: 'left'   },
    { label: '',             key: 'days',   align: 'center' },
    { label: 'AMOUNT (Rs.)', key: 'amt',    align: 'right'  },
  ],
};

function drawSectionColHeader(doc, y, key) {
  const cols = SECTION_COLS[key] || SECTION_COLS['Other Expenses'];
  fillRect(doc, M, y, PW, HEAD_H, '#3D3D3D');
  cols.forEach(({ label, key: k, align }) => {
    drawText(doc, label, CX[k] + 3, y + 5, {
      font: 'Helvetica-Bold', size: 7, color: COLOR.white, align, width: CW[k] - 4,
    });
  });
  colDividers(doc, y, HEAD_H);
  return y + HEAD_H;
}

// ─────────────────────────────────────────────────────────────────
// MAIN — one page per trip
// ─────────────────────────────────────────────────────────────────
function generateTripPdf(doc, trip, isFirstTrip) {
  if (!isFirstTrip) doc.addPage();

  let cy = M;

  // ── 1. TITLE BAR ─────────────────────────────────────────────
  fillRect(doc, M, cy, PW, 28, COLOR.dark);
  drawText(doc, 'TRAVELLING EXPENSES STATEMENT', M, cy + 9, {
    font: 'Helvetica-Bold', size: 13, color: COLOR.white, align: 'center', width: PW,
  });
  cy += 34;

  // ── 2. EMPLOYEE INFO GRID ────────────────────────────────────
  const INFO_CELL_H = 28;
  const LABEL_H     = 11;
  const C1W = PW * 0.58;
  const C2W = PW - C1W;
  const totalDays = (trip.expenses || []).reduce((s, e) => s + (e.daysCount || 0), 0);

  const infoRows = [
    { l1: 'EMPLOYEE NAME',   v1: trip.employeeName  || '—',
      l2: 'DESIGNATION',     v2: trip.designation   || '—' },
    { l1: 'STATION VISITED', v1: trip.stationVisited || '—',
      l2: 'DATE SUBMITTED',  v2: fmtDate(trip.createdAt) },
    { l1: 'PERIOD',
      v1: `${trip.periodFrom || ''} to ${trip.periodTo || ''}`,
      l2: 'TOTAL DAYS',
      v2: totalDays > 0 ? String(totalDays) : '—' },
  ];

  strokeRect(doc, M, cy, PW, INFO_CELL_H * infoRows.length, COLOR.border, 0.8);

  infoRows.forEach(({ l1, v1, l2, v2 }, i) => {
    const ry = cy + i * INFO_CELL_H;
    if (i > 0) hLine(doc, M, ry, PW, COLOR.border, 0.5);
    vLine(doc, M + C1W, ry, ry + INFO_CELL_H, COLOR.border, 0.5);

    fillRect(doc, M,      ry, C1W, LABEL_H, COLOR.labelBg);
    drawText(doc, l1, M + 5, ry + 3, { font: 'Helvetica-Bold', size: 6.5, color: COLOR.midGrey });
    drawText(doc, v1, M + 5, ry + LABEL_H + 4, { font: 'Helvetica-Bold', size: 9 });

    fillRect(doc, M + C1W, ry, C2W, LABEL_H, COLOR.labelBg);
    drawText(doc, l2, M + C1W + 5, ry + 3, { font: 'Helvetica-Bold', size: 6.5, color: COLOR.midGrey });
    drawText(doc, v2, M + C1W + 5, ry + LABEL_H + 4, { font: 'Helvetica-Bold', size: 9 });
  });

  cy += INFO_CELL_H * infoRows.length + 10;

  // ── 3+4. EXPENSE SECTIONS (each has its own column header) ─────
  const sections = [
    { num: 1, title: 'AIR / TRAIN / BUS FARE',          key: 'Air / Train / Bus'  },
    { num: 2, title: 'HOTEL BOOKING / LODGING CHARGE',   key: 'Hotel / Lodging'    },
    { num: 3, title: 'LOCAL CONVEYANCE',                 key: 'Local Conveyance'   },
    { num: 4, title: 'DAILY ALLOWANCES',                 key: 'Daily Allowance'    },
    { num: 5, title: 'OTHER EXPENSES',                   key: 'Other Expenses'     },
  ];

  const tableStartY = cy;

  sections.forEach(({ num, title, key }) => {
    const items = (trip.expenses || []).filter(e => e.expenseType === key);

    // Section title row
    fillRect(doc, M, cy, PW, SEC_H, COLOR.darkLite);
    hLine(doc, M, cy, PW, COLOR.border, 0.4);
    drawText(doc, `${num}.`, CX.sr, cy + 4, {
      font: 'Helvetica-Bold', size: 8.5, color: COLOR.dark, align: 'center', width: CW.sr,
    });
    drawText(doc, title, CX.detail + 3, cy + 5, {
      font: 'Helvetica-Bold', size: 8.5, color: COLOR.dark,
    });
    hLine(doc, M, cy + SEC_H, PW, COLOR.border, 0.4);
    cy += SEC_H;

    // Per-section column headers
    cy = drawSectionColHeader(doc, cy, key);

    if (items.length === 0) {
      fillRect(doc, M, cy, PW, ROW_H, COLOR.rowAlt);
      drawText(doc, 'No entries for this category', CX.detail + 3, cy + 4, {
        size: 7.5, color: COLOR.liteTxt,
      });
      hLine(doc, M, cy + ROW_H, PW, COLOR.border, 0.3);
      colDividers(doc, cy, ROW_H);
      cy += ROW_H;
    } else {
      items.forEach((item, idx) => {
        fillRect(doc, M, cy, PW, ROW_H, idx % 2 === 0 ? COLOR.white : COLOR.rowAlt);

        const letter = String.fromCharCode(97 + idx);
        drawText(doc, `${letter}.`, CX.sr, cy + 4, {
          size: 8, color: COLOR.greyTxt, align: 'center', width: CW.sr,
        });

        const det = (item.details || '').substring(0, 28) +
          ((item.details || '').length > 28 ? '\u2026' : '');
        drawText(doc, det || '\u2014', CX.detail + 3, cy + 4, { size: 8 });

        let fromVal, toVal, modeVal, daysVal;

        if (key === 'Air / Train / Bus') {
          // Travel mode (Train/Air/Bus) is relevant here
          fromVal = (item.travelFrom || '\u2014').substring(0, 14);
          toVal   = (item.travelTo   || '\u2014').substring(0, 12);
          modeVal = (item.travelMode || '\u2014').substring(0, 9);
          daysVal = item.daysCount > 0 ? String(item.daysCount) : '\u2014';
        } else if (key === 'Local Conveyance') {
          // No travel mode — vehicle type/notes stored in details field
          fromVal = (item.travelFrom || '\u2014').substring(0, 14);
          toVal   = (item.travelTo   || '\u2014').substring(0, 12);
          modeVal = (item.details    || '\u2014').substring(0, 12); // vehicle type from notes
          daysVal = item.daysCount > 0 ? String(item.daysCount) : '\u2014';
        } else if (key === 'Hotel / Lodging') {
          fromVal = item.travelFrom ? item.travelFrom.substring(0, 14) : '\u2014';
          toVal   = item.daysCount > 0 ? `${item.daysCount} night${item.daysCount !== 1 ? 's' : ''}` : '\u2014';
          modeVal = item.travelMode ? item.travelMode.substring(0, 9) : '\u2014';
          daysVal = item.daysCount > 0 ? String(item.daysCount) : '\u2014';
        } else if (key === 'Daily Allowance') {
          fromVal = item.travelFrom ? item.travelFrom.substring(0, 14) : '\u2014';
          toVal   = item.travelTo   ? item.travelTo.substring(0, 12)   : '\u2014';
          modeVal = item.ratePerDay > 0 ? `Rs.${item.ratePerDay}` : '\u2014';
          daysVal = item.daysCount > 0 ? String(item.daysCount) : '\u2014';
        } else {
          // Other Expenses
          fromVal = item.travelFrom ? item.travelFrom.substring(0, 14) : '\u2014';
          toVal   = item.travelTo   ? item.travelTo.substring(0, 12)   : '\u2014';
          modeVal = '\u2014';
          daysVal = '\u2014';
        }

        drawText(doc, fromVal, CX.from + 3, cy + 4, { size: 8 });
        drawText(doc, toVal,   CX.to   + 3, cy + 4, { size: 8 });
        drawText(doc, modeVal, CX.mode + 3, cy + 4, { size: 7.5, color: COLOR.greyTxt });
        drawText(doc, daysVal, CX.days,     cy + 4, {
          size: 8, color: COLOR.greyTxt, align: 'center', width: CW.days,
        });

        drawText(doc, fmtRs(item.amount), CX.amt, cy + 4, {
          font: 'Helvetica-Bold', size: 8.5, align: 'right', width: CW.amt - 3,
        });

        hLine(doc, M, cy + ROW_H, PW, COLOR.border, 0.3);
        colDividers(doc, cy, ROW_H);
        cy += ROW_H;
      });
    }
  });

  strokeRect(doc, M, tableStartY, PW, cy - tableStartY, COLOR.border, 0.7);
  cy += 6;

  // ── 5. TOTALS BLOCK ──────────────────────────────────────────
  const TW          = PW * 0.52;
  const TX          = M + PW - TW;
  const T_ROW_H     = 22;
  const LABEL_COL_W = TW * 0.60;

  const totals = [
    { label: 'TOTAL EXPENSES',              value: fmtRs(trip.totalAmount),   hi: false },
    { label: 'ADVANCE / IMPREST',           value: fmtRs(trip.advanceAmount), hi: false },
    { label: 'AMOUNT PAYABLE / REFUNDABLE', value: fmtRs(trip.payableAmount), hi: true  },
  ];

  strokeRect(doc, TX, cy, TW, T_ROW_H * totals.length, COLOR.border, 0.8);

  totals.forEach(({ label, value, hi }, i) => {
    const ry = cy + i * T_ROW_H;
    if (i > 0) hLine(doc, TX, ry, TW, COLOR.border, 0.5);
    if (hi) fillRect(doc, TX, ry, TW, T_ROW_H, COLOR.totalHl);
    vLine(doc, TX + LABEL_COL_W, ry, ry + T_ROW_H, COLOR.border, 0.5);
    drawText(doc, label, TX + 6, ry + 7, {
      font: hi ? 'Helvetica-Bold' : 'Helvetica',
      size: hi ? 8 : 7.5,
      color: hi ? COLOR.dark : COLOR.greyTxt,
    });
    drawText(doc, value, TX + LABEL_COL_W + 4, ry + 7, {
      font: 'Helvetica-Bold',
      size: hi ? 9 : 8.5,
      color: hi ? COLOR.dark : COLOR.black,
      align: 'right',
      width: TW - LABEL_COL_W - 8,
    });
  });

  cy += T_ROW_H * totals.length + 12;

  // ── 6. STATUS BADGE ──────────────────────────────────────────
  const s = trip.status || 'pending';
  const [bg, bdr, fg] = s === 'approved' ? [COLOR.greenBg, COLOR.greenBdr, COLOR.greenTxt]
    : s === 'rejected'                    ? [COLOR.redBg,   COLOR.redBdr,   COLOR.redTxt]
    :                                       [COLOR.ambBg,   COLOR.ambBdr,   COLOR.ambTxt];
  const badgeW = 110;
  fillRect(doc, M, cy, badgeW, 20, bg);
  strokeRect(doc, M, cy, badgeW, 20, bdr, 0.8);
  drawText(doc, `STATUS: ${s.toUpperCase()}`, M + 6, cy + 6, {
    font: 'Helvetica-Bold', size: 8.5, color: fg,
  });
  if (trip.adminNote) {
    drawText(doc, `Note: ${trip.adminNote}`, M + badgeW + 10, cy + 6, {
      size: 7.5, color: COLOR.greyTxt,
    });
  }
  cy += 30;

  // ── 7. SIGNATURE ROW ─────────────────────────────────────────
  const sigH    = 38;
  const sigColW = PW / 3;
  fillRect(doc, M, cy, PW, sigH, COLOR.sigBg);
  strokeRect(doc, M, cy, PW, sigH, COLOR.border, 0.7);
  ['EMPLOYEE SIGNATURE', 'ACCOUNTS DEPT.', 'MANAGER / HOD'].forEach((label, i) => {
    const sx = M + i * sigColW;
    if (i > 0) vLine(doc, sx, cy, cy + sigH, COLOR.border, 0.5);
    drawText(doc, label, sx, cy + 7, {
      font: 'Helvetica-Bold', size: 7, color: COLOR.midGrey, align: 'center', width: sigColW,
    });
    hLine(doc, sx + sigColW * 0.15, cy + sigH - 8, sigColW * 0.70, '#AAAAAA', 0.5);
  });
  cy += sigH + 8;

  // ── 8. FOOTER ────────────────────────────────────────────────
  hLine(doc, M, cy, PW, COLOR.border, 0.4);
  drawText(doc,
    `Generated: ${fmtDate(trip.createdAt)}   |   Trip ID: ${trip._id}`,
    M, cy + 5, { size: 6.5, color: COLOR.liteTxt, align: 'right', width: PW });
}

async function downloadReceiptToTemp(url, destPath) {
  try {
    const res = await axios({ method: 'GET', url, responseType: 'stream', timeout: 30000 });
    await new Promise((resolve, reject) => {
      const ws = fs.createWriteStream(destPath);
      res.data.pipe(ws);
      ws.on('finish', resolve);
      ws.on('error', reject);
    });
    return true;
  } catch (e) {
    console.error(`Receipt download failed (${url}): ${e.message}`);
    return false;
  }
}

exports.exportUserExpensesPdf = async (req, res) => {
  const { userId } = req.params;
  const { startDate, endDate } = req.query;
  try {
    const User = require('../models/User');
    const user = await User.findById(userId).select('-password');
    if (!user) return res.status(404).json({ success: false, message: 'User not found' });

    let query = { userId };
    if (startDate && endDate) {
      query.createdAt = {
        $gte: new Date(startDate),
        $lte: new Date(new Date(endDate).setHours(23, 59, 59)),
      };
    }
    const trips = await Trip.find(query).sort({ createdAt: -1 });
    if (!trips.length)
      return res.status(404).json({ success: false, message: 'No expense trips found' });

    const doc = new PDFDocument({ margin: M, size: 'A4', bufferPages: true });
    const filename = `${user.employeeId}_expenses_${Date.now()}.pdf`;
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    doc.pipe(res);
    trips.forEach((trip, i) => generateTripPdf(doc, trip, i === 0));
    doc.end();
    console.log(`✅ Expense PDF: ${filename} (${trips.length} trips)`);
  } catch (error) {
    console.error('❌ Expense PDF error:', error);
    if (!res.headersSent) res.status(500).json({ success: false, message: error.message });
  }
};

exports.exportAllExpensesPdf = async (req, res) => {
  const { startDate, endDate } = req.query;
  try {
    let query = {};
    if (startDate && endDate) {
      query.createdAt = {
        $gte: new Date(startDate),
        $lte: new Date(new Date(endDate).setHours(23, 59, 59)),
      };
    }
    const trips = await Trip.find(query).sort({ employeeId: 1, createdAt: -1 });
    if (!trips.length)
      return res.status(404).json({ success: false, message: 'No expense trips found' });

    const doc = new PDFDocument({ margin: M, size: 'A4', bufferPages: true });
    const filename = `all_expenses_${Date.now()}.pdf`;
    res.setHeader('Content-Type', 'application/pdf');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    doc.pipe(res);
    trips.forEach((trip, i) => generateTripPdf(doc, trip, i === 0));
    doc.end();
    console.log(`✅ All-expenses PDF: ${filename} (${trips.length} trips)`);
  } catch (error) {
    console.error('❌ All expenses PDF error:', error);
    if (!res.headersSent) res.status(500).json({ success: false, message: error.message });
  }
};

exports.exportAllExpensesCSV = async (req, res) => {
  const { startDate, endDate } = req.query;
  try {
    let query = {};
    if (startDate && endDate) {
      query.createdAt = {
        $gte: new Date(startDate),
        $lte: new Date(new Date(endDate).setHours(23, 59, 59)),
      };
    }
    const trips = await Trip.find(query).sort({ employeeId: 1, createdAt: -1 });

    let csv = 'Employee ID,Employee Name,Designation,Station Visited,Period From,Period To,' +
              'Expense Type,Details,Travel From,Travel To,Days,Rate/Day,Line Amount,' +
              'Trip Total,Advance,Payable,Status,Receipt URL,Submitted On\n';

    trips.forEach(trip => {
      if (!trip.expenses.length) {
        csv += `"${trip.employeeId}","${trip.employeeName}","${trip.designation}",` +
               `"${trip.stationVisited}","${trip.periodFrom}","${trip.periodTo}",` +
               `,,,,,,,"${trip.totalAmount}","${trip.advanceAmount}","${trip.payableAmount}",` +
               `"${trip.status}",,${fmtDate(trip.createdAt)}\n`;
      } else {
        trip.expenses.forEach((exp, i) => {
          csv += `"${trip.employeeId}","${trip.employeeName}","${trip.designation}",` +
                 `"${trip.stationVisited}","${trip.periodFrom}","${trip.periodTo}",` +
                 `"${exp.expenseType}","${exp.details || ''}","${exp.travelFrom || ''}","${exp.travelTo || ''}",` +
                 `${exp.daysCount || 0},${exp.ratePerDay || 0},${exp.amount || 0},` +
                 `${i === 0 ? trip.totalAmount : ''},${i === 0 ? trip.advanceAmount : ''},` +
                 `${i === 0 ? trip.payableAmount : ''},"${i === 0 ? trip.status : ''}",` +
                 `"${exp.receiptUrl || ''}","${i === 0 ? fmtDate(trip.createdAt) : ''}"\n`;
        });
      }
    });

    const filename = `all_expenses_${Date.now()}.csv`;
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
    res.status(200).send(csv);
    console.log(`✅ All-expenses CSV: ${filename}`);
  } catch (error) {
    console.error('❌ Expenses CSV error:', error);
    res.status(500).json({ success: false, message: error.message });
  }
};

exports.generateTripPdf       = generateTripPdf;
exports.downloadReceiptToTemp = downloadReceiptToTemp;