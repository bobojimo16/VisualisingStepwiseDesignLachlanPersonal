/**
 * Styling for the default joint.js shapes.
 */
//Modify the transition element to place the label inside and not above.

joint.shapes.pn.Transition = joint.shapes.basic.Generic.extend({

  markup: '<g class="rotatable"><g class="scalable"><rect class="root"/></g></g><text class="label"/>',

  defaults: _.defaultsDeep({

    type: 'pn.Transition',
    size: { width: 20, height: 60 },
    attrs: {
      'rect': {
        width: 12,
        height: 50,
        fill: '#000000',
        stroke: '#000000'
      },
      '.label': {
        'text-anchor': 'middle',
        'ref-x': .5,
        'ref-y': 23,
        ref: 'rect',
        fill: '#FFFFFF',
        'font-size': 12
      }
    }

  }, joint.shapes.basic.Generic.prototype.defaults)
});
