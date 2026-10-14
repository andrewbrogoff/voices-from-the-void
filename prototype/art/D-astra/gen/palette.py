# SPDX-License-Identifier: MPL-2.0
"""Opaline Resonance: ordered dark-to-light material ramps."""
RAMPS = {
    "copper": ["#623C43", "#A95748", "#D98262", "#F2B18A", "#FFE2BF"],
    "brass": ["#53534A", "#85845B", "#B6B974", "#DCE299", "#F5F2C7"],
    "glass": ["#405568", "#7193A0", "#A9CCD0", "#DDF0E8", "#FFF9E5"],
    "amethyst": ["#544975", "#8D75B6", "#C8ADE0", "#F0E0F3"],
    "opal": ["#709FBC", "#9DD9CF", "#EDB2AB", "#FFF9E5"],
    "hush": ["#252A32", "#515965", "#89949C", "#BDC6C8", "#E6E9DF"],
}


def rgba(value, alpha=255):
    return tuple(bytes.fromhex(value.lstrip("#"))) + (alpha,)
