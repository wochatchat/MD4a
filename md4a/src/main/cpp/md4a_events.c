/*
 * md4a_events.c — md4c callbacks that serialize parse events into a flat
 * binary buffer. Pure C, no JNI: the same core is unit-tested on the host.
 *
 * Callback contract: md4c never reports error inside events; aborting parse
 * (non-zero return) is only needed on allocation failure.
 */
#include "md4a_events.h"
#include "md4c.h"

#include <stdlib.h>
#include <string.h>

static int grow(md4a_buf *b, size_t need) {
    if (b->len + need <= b->cap) return 0;
    size_t ncap = b->cap ? b->cap * 2 : 4096;
    while (ncap < b->len + need) ncap *= 2;
    uint8_t *nd = realloc(b->data, ncap);
    if (!nd) return -1;
    b->data = nd;
    b->cap = ncap;
    return 0;
}

int md4a_buf_init(md4a_buf *b) {
    b->data = NULL; b->len = 0; b->cap = 0;
    return grow(b, 4096);
}

void md4a_buf_free(md4a_buf *b) {
    free(b->data);
    b->data = NULL; b->len = 0; b->cap = 0;
}

int md4a_buf_u8(md4a_buf *b, uint8_t v) {
    if (grow(b, 1)) return -1;
    b->data[b->len++] = v;
    return 0;
}

int md4a_buf_u32(md4a_buf *b, uint32_t v) {
    if (grow(b, 4)) return -1;
    b->data[b->len++] = (uint8_t)(v & 0xFF);
    b->data[b->len++] = (uint8_t)((v >> 8) & 0xFF);
    b->data[b->len++] = (uint8_t)((v >> 16) & 0xFF);
    b->data[b->len++] = (uint8_t)((v >> 24) & 0xFF);
    return 0;
}

int md4a_buf_str(md4a_buf *b, const char *s, size_t n) {
    if (md4a_buf_u32(b, (uint32_t)n)) return -1;
    if (n == 0) return 0;
    if (grow(b, n)) return -1;
    memcpy(b->data + b->len, s, n);
    b->len += n;
    return 0;
}

int md4a_buf_opt_str(md4a_buf *b, const char *s, size_t n) {
    if (s == NULL) return md4a_buf_u8(b, 0x00);
    if (md4a_buf_u8(b, 0x01)) return -1;
    return md4a_buf_str(b, s, n);
}

/* ── md4c callbacks ─────────────────────────────────────────────────── */

static int cb_enter_block(MD_BLOCKTYPE type, void *detail, void *udata) {
    md4a_buf *b = (md4a_buf *)udata;
    switch (type) {
    case MD_BLOCK_DOC: return 0;
    case MD_BLOCK_QUOTE: return md4a_buf_u8(b, EV_QUOTE_BEGIN);
    case MD_BLOCK_UL: return md4a_buf_u8(b, EV_LIST_BEGIN) | md4a_buf_u8(b, 0) | md4a_buf_u32(b, 1);
    case MD_BLOCK_OL: {
        MD_BLOCK_OL_DETAIL *d = (MD_BLOCK_OL_DETAIL *)detail;
        return md4a_buf_u8(b, EV_LIST_BEGIN) | md4a_buf_u8(b, 1) | md4a_buf_u32(b, (uint32_t)d->start);
    }
    case MD_BLOCK_LI: {
        MD_BLOCK_LI_DETAIL *d = (MD_BLOCK_LI_DETAIL *)detail;
        return md4a_buf_u8(b, EV_LI_BEGIN)
             | md4a_buf_u8(b, d->is_task ? 1 : 0)
             | md4a_buf_u8(b, (d->is_task && d->task_mark != ' ') ? 1 : 0);
    }
    case MD_BLOCK_H: {
        MD_BLOCK_H_DETAIL *d = (MD_BLOCK_H_DETAIL *)detail;
        return md4a_buf_u8(b, EV_H_BEGIN) | md4a_buf_u8(b, (uint8_t)d->level);
    }
    case MD_BLOCK_P: return md4a_buf_u8(b, EV_P_BEGIN);
    case MD_BLOCK_CODE: {
        MD_BLOCK_CODE_DETAIL *d = (MD_BLOCK_CODE_DETAIL *)detail;
        return md4a_buf_u8(b, EV_CODE_BEGIN) | md4a_buf_str(b, d->lang.text, d->lang.size);
    }
    case MD_BLOCK_HTML: return md4a_buf_u8(b, EV_HTML_BEGIN);
    case MD_BLOCK_TABLE: return md4a_buf_u8(b, EV_TABLE_BEGIN);
    case MD_BLOCK_THEAD: return md4a_buf_u8(b, EV_THEAD_BEGIN);
    case MD_BLOCK_TBODY: return md4a_buf_u8(b, EV_TBODY_BEGIN);
    case MD_BLOCK_TR: return md4a_buf_u8(b, EV_TR_BEGIN);
    case MD_BLOCK_TH: case MD_BLOCK_TD: {
        MD_BLOCK_TD_DETAIL *d = (MD_BLOCK_TD_DETAIL *)detail;
        uint8_t align = (d->align == MD_ALIGN_CENTER) ? 1 : (d->align == MD_ALIGN_RIGHT) ? 2 : 0;
        return md4a_buf_u8(b, EV_TD_BEGIN) | md4a_buf_u8(b, align);
    }
    case MD_BLOCK_HR: return md4a_buf_u8(b, EV_HR);
    default: return 0; /* footnotes/admonitions etc. — transparent */
    }
}

static int cb_leave_block(MD_BLOCKTYPE type, void *detail, void *udata) {
    md4a_buf *b = (md4a_buf *)udata;
    switch (type) {
    case MD_BLOCK_QUOTE: return md4a_buf_u8(b, EV_QUOTE_END);
    case MD_BLOCK_UL: case MD_BLOCK_OL: return md4a_buf_u8(b, EV_LIST_END);
    case MD_BLOCK_LI: return md4a_buf_u8(b, EV_LI_END);
    case MD_BLOCK_H: return md4a_buf_u8(b, EV_H_END);
    case MD_BLOCK_P: return md4a_buf_u8(b, EV_P_END);
    case MD_BLOCK_CODE: return md4a_buf_u8(b, EV_CODE_END);
    case MD_BLOCK_HTML: return md4a_buf_u8(b, EV_HTML_END);
    case MD_BLOCK_TABLE: return md4a_buf_u8(b, EV_TABLE_END);
    case MD_BLOCK_THEAD: return md4a_buf_u8(b, EV_THEAD_END);
    case MD_BLOCK_TBODY: return md4a_buf_u8(b, EV_TBODY_END);
    case MD_BLOCK_TR: return md4a_buf_u8(b, EV_TR_END);
    case MD_BLOCK_TH: case MD_BLOCK_TD: return md4a_buf_u8(b, EV_TD_END);
    default: return 0;
    }
}

static int cb_enter_span(MD_SPANTYPE type, void *detail, void *udata) {
    md4a_buf *b = (md4a_buf *)udata;
    switch (type) {
    case MD_SPAN_EM: return md4a_buf_u8(b, EV_EM_BEGIN) | md4a_buf_u8(b, 0);
    case MD_SPAN_STRONG: return md4a_buf_u8(b, EV_EM_BEGIN) | md4a_buf_u8(b, 1);
    case MD_SPAN_DEL: return md4a_buf_u8(b, EV_DEL_BEGIN);
    case MD_SPAN_A: {
        MD_SPAN_A_DETAIL *d = (MD_SPAN_A_DETAIL *)detail;
        return md4a_buf_u8(b, EV_A_BEGIN) | md4a_buf_str(b, d->href.text, d->href.size)
             | md4a_buf_opt_str(b, d->title.text, d->title.size);
    }
    case MD_SPAN_IMG: {
        MD_SPAN_IMG_DETAIL *d = (MD_SPAN_IMG_DETAIL *)detail;
        return md4a_buf_u8(b, EV_IMG_BEGIN) | md4a_buf_str(b, d->src.text, d->src.size)
             | md4a_buf_opt_str(b, d->title.text, d->title.size);
    }
    case MD_SPAN_CODE: return md4a_buf_u8(b, EV_CODESPAN_BEGIN);
    default: return 0; /* wikilink/latex/footnote ref etc. — transparent */
    }
}

static int cb_leave_span(MD_SPANTYPE type, void *detail, void *udata) {
    md4a_buf *b = (md4a_buf *)udata;
    switch (type) {
    case MD_SPAN_EM: case MD_SPAN_STRONG: return md4a_buf_u8(b, EV_EM_END);
    case MD_SPAN_DEL: return md4a_buf_u8(b, EV_DEL_END);
    case MD_SPAN_A: return md4a_buf_u8(b, EV_A_END);
    case MD_SPAN_IMG: return md4a_buf_u8(b, EV_IMG_END);
    case MD_SPAN_CODE: return md4a_buf_u8(b, EV_CODESPAN_END);
    default: return 0;
    }
}

static int cb_text(MD_TEXTTYPE type, const MD_CHAR *text, MD_SIZE size, void *udata) {
    md4a_buf *b = (md4a_buf *)udata;
    switch (type) {
    case MD_TEXT_NORMAL: return md4a_buf_u8(b, EV_TEXT) | md4a_buf_str(b, text, size);
    case MD_TEXT_ENTITY: return md4a_buf_u8(b, EV_ENTITY) | md4a_buf_str(b, text, size);
    case MD_TEXT_SOFTBR: return md4a_buf_u8(b, EV_SOFTBR);
    case MD_TEXT_BR: return md4a_buf_u8(b, EV_BR);
    case MD_TEXT_CODE: return md4a_buf_u8(b, EV_TEXT) | md4a_buf_str(b, text, size);
    case MD_TEXT_HTML: return md4a_buf_u8(b, EV_HTML_INLINE) | md4a_buf_str(b, text, size);
    default: /* NULLCHAR etc. → literal text */ return md4a_buf_u8(b, EV_TEXT) | md4a_buf_str(b, text, size);
    }
}

/* Public entry: parse `input` and append the event stream to `b`.
 * Returns 0 on success, -1 on OOM/abort. */
int md4a_parse_to_events(unsigned flags, const char *input, size_t size, md4a_buf *b) {
    MD_PARSER parser;
    memset(&parser, 0, sizeof(parser));
    parser.abi_version = 0;
    parser.flags = flags;
    parser.enter_block = cb_enter_block;
    parser.leave_block = cb_leave_block;
    parser.enter_span = cb_enter_span;
    parser.leave_span = cb_leave_span;
    parser.text = cb_text;
    parser.debug_log = NULL;
    parser.syntax = NULL;
    return md_parse(input, size, &parser, b) == 0 ? 0 : -1;
}
